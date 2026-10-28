package com.example.autodoc.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.autodoc.FlowStep;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.env.MockEnvironment;

class FlowStepInterceptorTest {

    public static class Fixture {

        @FlowStep(action = "Greet", participant = "SYSTEM", note = "Greeting #{#name}")
        public String greet(String name) {
            return "Hello " + name;
        }

        @FlowStep(action = "Configured", participant = "SYSTEM", note = "Mode #{#property('mode')}")
        public void configured() {
        }

        @FlowStep(action = "Broken note", participant = "SYSTEM", note = "#{#missing.length()}")
        public String brokenNote() {
            return "still runs";
        }

        @FlowStep(action = "Fail", participant = "SYSTEM")
        public void fail() {
            throw new IllegalArgumentException("boom");
        }
    }

    private final List<Observation.Context> stopped = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final ObservationRegistry registry = ObservationRegistry.create();

    FlowStepInterceptorTest() {
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }

            @Override
            public void onEvent(Observation.Event event, Observation.Context context) {
                notes.add(event.getContextualName());
            }

            @Override
            public void onStop(Observation.Context context) {
                stopped.add(context);
            }
        });
    }

    private Fixture proxy() {
        ProxyFactory factory = new ProxyFactory(new Fixture());
        factory.setProxyTargetClass(true);
        factory.addAdvisor(AutodocAutoConfiguration.advisor(() -> registry, new MockEnvironment().withProperty("mode", "fast")));
        return (Fixture) factory.getProxy();
    }

    private Observation.Context only() {
        assertThat(stopped).hasSize(1);
        return stopped.get(0);
    }

    @Test
    void recordsTheStepWithItsParticipantAndNote() {
        assertThat(proxy().greet("Ada")).isEqualTo("Hello Ada");

        Observation.Context context = only();
        assertThat(context.getName()).isEqualTo("autodoc.step");
        assertThat(context.getLowCardinalityKeyValue(AutodocObservationKeys.STEP_KEY).getValue()).isEqualTo("Greet");
        assertThat(context.getLowCardinalityKeyValue(AutodocObservationKeys.PARTICIPANT_KEY).getValue()).isEqualTo("SYSTEM");
        assertThat(notes).containsExactly("Greeting Ada");
    }

    @Test
    void notesCanReadConfiguration() {
        proxy().configured();

        assertThat(notes).containsExactly("Mode fast");
    }

    @Test
    void aBrokenNoteIsRecordedButNeverAffectsTheCall() {
        assertThat(proxy().brokenNote()).isEqualTo("still runs");

        assertThat(notes).isEmpty();
        assertThat(only().getHighCardinalityKeyValue(AutodocObservationKeys.PROBLEM_KEY).getValue())
                .startsWith("note error on Broken note");
    }

    @Test
    void recordsTheErrorOfAFailingStep() {
        assertThatThrownBy(() -> proxy().fail()).isInstanceOf(IllegalArgumentException.class);

        assertThat(only().getError()).isInstanceOf(IllegalArgumentException.class);
    }
}
