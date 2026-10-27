package com.example.flowcap.test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class FlowcapRulesTest {

    @Test
    void unannotatedCallsToClientInterfacesFailButReadingClientDataDoesNot() {
        assertThatThrownBy(() -> FlowcapRules.downstreamCallsAreSteps("com.example.flowcap.test.clientcall.client..")
                .check(new ClassFileImporter().importPackages("com.example.flowcap.test.clientcall")))
                .hasMessageContaining("was violated (1 times)")
                .hasMessageContaining("Caller.fetchUnannotated()");
    }

    @Test
    void fixtureFollowsTheRules() {
        var classes = new ClassFileImporter().importPackages("com.example.flowcap.test.fixture");
        assertThatCode(() -> {
            FlowcapRules.namesAreUnique(classes);
            FlowcapRules.participantsAreDeclared(classes);
            FlowcapRules.expressionsParse(classes);
            FlowcapRules.flowMethodsAreProxyable.check(classes);
            FlowcapRules.noSelfInvocation.check(classes);
        }).doesNotThrowAnyException();
    }
}
