package com.example.autodoc.boot;

import com.example.autodoc.FlowStep;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.PropertyResolver;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;

/**
 * Records each {@link FlowStep} call as an autodoc.step observation. A note that fails to evaluate never
 * affects the call: it's logged and recorded on the observation, where the scenario tests fail on it.
 */
public class FlowStepInterceptor implements MethodInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FlowStepInterceptor.class);

    private final Supplier<ObservationRegistry> registry;
    private final MethodHandle property;
    private final Map<Method, StepMethod> stepMethods = new ConcurrentHashMap<>();

    /** @param properties the app's configuration, for {@code #property('key')} in notes */
    public FlowStepInterceptor(Supplier<ObservationRegistry> registry, PropertyResolver properties) {
        this.registry = registry;
        this.property = FlowExpressions.propertyFunction(properties);
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        StepMethod step = stepMethods.computeIfAbsent(method, StepMethod::of);
        Observation observation = Observation.createNotStarted(AutodocObservationKeys.OBSERVATION_NAME, registry.get())
                .contextualName(step.action())
                .lowCardinalityKeyValue(AutodocObservationKeys.STEP_KEY, step.action())
                .lowCardinalityKeyValue(AutodocObservationKeys.PARTICIPANT_KEY, step.participant())
                .start();
        try (Observation.Scope scope = observation.openScope()) {
            if (step.note() != null) {
                recordNote(observation, step, method, invocation.getArguments());
            }
            return invocation.proceed();
        } catch (Throwable e) {
            observation.error(e);
            throw e;
        } finally {
            observation.stop();
        }
    }

    private void recordNote(Observation observation, StepMethod step, Method method, Object[] arguments) {
        try {
            String text = step.note().getValue(FlowExpressions.context(method, arguments, property), String.class);
            observation.event(Observation.Event.of(AutodocObservationKeys.NOTE_EVENT, text));
        } catch (EvaluationException e) {
            String problem = "note error on " + step.action() + ": " + e.getMessage();
            log.warn(problem, e);
            observation.highCardinalityKeyValue(AutodocObservationKeys.PROBLEM_KEY, problem);
        }
    }

    /** Annotation data and the parsed note, built once per method on first call. */
    record StepMethod(String action, String participant, Expression note) {

        static StepMethod of(Method method) {
            FlowStep step = AnnotatedElementUtils.findMergedAnnotation(method, FlowStep.class);
            return new StepMethod(step.action(), step.participant(),
                    step.note().isEmpty() ? null : FlowExpressions.parseNote(step.note()));
        }
    }
}
