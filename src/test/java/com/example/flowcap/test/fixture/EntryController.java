package com.example.flowcap.test.fixture;

import com.example.flowcap.FlowStep;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EntryController {

    private final Orchestrator orchestrator;

    public EntryController(Orchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @FlowStep(action = "Receive", participant = Ids.APP)
    public String receive(String in) {
        return orchestrator.run(in, in.startsWith("f"));
    }
}
