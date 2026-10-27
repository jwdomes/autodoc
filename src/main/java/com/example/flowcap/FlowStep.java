package com.example.flowcap;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a step in the request flow. Inert unless flowcap.observations.enabled=true; read by the flowcap
 * tests at build time.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FlowStep {

    /** What is happening: node and message text, and the step's identity. */
    String action();

    /** Id of the system doing the work, declared by a {@link FlowParticipant} constant. */
    String participant();

    /**
     * Optional SpEL template evaluated against the method's arguments before it runs, shown as a note in
     * scenario diagrams, e.g. {@code "Using passport #{#request.documentNumber()}"}.
     */
    String note() default "";
}
