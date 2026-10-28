package com.example.autodoc.boot;

/** Names and tag keys of the observations the interceptor records. */
public final class AutodocObservationKeys {

    public static final String OBSERVATION_NAME = "autodoc.step";
    public static final String STEP_KEY = "autodoc.step";
    public static final String PARTICIPANT_KEY = "autodoc.participant";
    /** High-cardinality key holding a note evaluation error, when there was one. */
    public static final String PROBLEM_KEY = "autodoc.problem";
    public static final String NOTE_EVENT = "note";

    private AutodocObservationKeys() {
    }
}
