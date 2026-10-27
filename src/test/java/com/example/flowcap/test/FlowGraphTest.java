package com.example.flowcap.test;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class FlowGraphTest {

    private static final String FIXTURE = "com.example.flowcap.test.fixture";

    @Test
    void linksStepsThroughHelpersAndHangsErrorHandlersOffControllers() {
        JavaClasses classes = new ClassFileImporter().importPackages(FIXTURE);

        String mermaid = new FlowGraph(classes, FIXTURE, FlowParticipants.labels(classes)).mermaid();

        assertThat(mermaid).isEqualTo("""
                flowchart TD
                    subgraph APP["App"]
                        map_failure["Map failure"]
                        orchestrate["Orchestrate"]
                        receive["Receive"]
                    end
                    subgraph BACKEND["Backend"]
                        call_fast["Call fast"]
                        call_one["Call one"]
                        call_slow["Call slow"]
                        call_two["Call two"]
                    end
                    orchestrate --> call_fast
                    orchestrate --> call_one
                    orchestrate --> call_slow
                    orchestrate --> call_two
                    receive --> orchestrate
                    receive -.->|"IllegalStateException"| map_failure
                """);
    }
}
