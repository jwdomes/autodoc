package com.example.autodoc;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a participant on a {@code static final String} constant holding its id. Labels are read only
 * by the autodoc tests.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FlowParticipant {

    /** What readers see: subgraph titles and lifeline names. */
    String label();
}
