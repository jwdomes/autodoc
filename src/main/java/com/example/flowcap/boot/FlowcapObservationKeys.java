package com.example.flowcap.boot;

/** Names and tag keys of the observations the interceptor records. */
public final class FlowcapObservationKeys {

    public static final String OBSERVATION_NAME = "flowcap.step";
    public static final String STEP_KEY = "flowcap.step";
    public static final String PARTICIPANT_KEY = "flowcap.participant";
    /** High-cardinality key holding a note evaluation error, when there was one. */
    public static final String PROBLEM_KEY = "flowcap.problem";
    public static final String NOTE_EVENT = "note";

    private FlowcapObservationKeys() {
    }
}
