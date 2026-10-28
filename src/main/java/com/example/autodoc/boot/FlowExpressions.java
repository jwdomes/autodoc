package com.example.autodoc.boot;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.env.PropertyResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.StringUtils;

/** Parses and evaluates @FlowStep note templates against a method's arguments. */
public final class FlowExpressions {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final TemplateParserContext TEMPLATE = new TemplateParserContext();
    private static final ParameterNameDiscoverer PARAMETER_NAMES = new DefaultParameterNameDiscoverer();
    private static final Method HAS_TEXT;

    static {
        try {
            HAS_TEXT = StringUtils.class.getMethod("hasText", String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private FlowExpressions() {
    }

    public static Expression parseNote(String template) {
        return PARSER.parseExpression(template, TEMPLATE);
    }

    /**
     * Arguments are available as {@code #parameterName}; {@code #hasText(String)} is registered, and
     * {@code #property('key')} when {@code property} is non-null.
     *
     * @param property from {@link #propertyFunction}, or null where no configuration is available
     */
    public static EvaluationContext context(Method method, Object[] arguments, MethodHandle property) {
        MethodBasedEvaluationContext context =
                new MethodBasedEvaluationContext(null, method, arguments, PARAMETER_NAMES);
        context.registerFunction("hasText", HAS_TEXT);
        if (property != null) {
            context.registerFunction("property", property);
        }
        return context;
    }

    /** {@code #property('key')}: the app's configuration value for key, or null when unset. */
    public static MethodHandle propertyFunction(PropertyResolver properties) {
        try {
            return MethodHandles.publicLookup()
                    .findVirtual(PropertyResolver.class, "getProperty", MethodType.methodType(String.class, String.class))
                    .bindTo(properties);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
