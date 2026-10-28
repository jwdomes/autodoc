package com.example.autodoc.test.fixture;

import com.example.autodoc.FlowStep;

public class Backend {

    @FlowStep(action = "Call one", participant = Ids.BACKEND)
    public void callOne(String in) {
    }

    @FlowStep(action = "Call two", participant = Ids.BACKEND)
    public void callTwo(String in) {
    }

    @FlowStep(action = "Call fast", participant = Ids.BACKEND)
    public void callFast(String in) {
    }

    @FlowStep(action = "Call slow", participant = Ids.BACKEND)
    public void callSlow(String in) {
    }
}
