package com.example.flowcap.test.fixture;

import com.example.flowcap.FlowParticipant;

public final class Ids {

    @FlowParticipant(label = "App")
    public static final String APP = "APP";

    @FlowParticipant(label = "Backend")
    public static final String BACKEND = "BACKEND";

    private Ids() {
    }
}
