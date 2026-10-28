package com.example.autodoc.test.fixture;

import com.example.autodoc.FlowStep;

public class Orchestrator {

    private final Backend backend;

    public Orchestrator(Backend backend) {
        this.backend = backend;
    }

    @FlowStep(action = "Orchestrate", participant = Ids.APP)
    public String run(String in, boolean fast) {
        if (fast) {
            backend.callFast(in);
        } else {
            backend.callSlow(in);
        }
        if (!in.isEmpty()) {
            backend.callOne(in);
        }
        helper(in);
        return in;
    }

    private void helper(String in) {
        backend.callTwo(in);
    }
}
