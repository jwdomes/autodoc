package com.example.autodoc.test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class AutodocRulesTest {

    @Test
    void unannotatedCallsToClientInterfacesFailButReadingClientDataDoesNot() {
        assertThatThrownBy(() -> AutodocRules.downstreamCallsAreSteps("com.example.autodoc.test.clientcall.client..")
                .check(new ClassFileImporter().importPackages("com.example.autodoc.test.clientcall")))
                .hasMessageContaining("was violated (1 times)")
                .hasMessageContaining("Caller.fetchUnannotated()");
    }

    @Test
    void fixtureFollowsTheRules() {
        var classes = new ClassFileImporter().importPackages("com.example.autodoc.test.fixture");
        assertThatCode(() -> {
            AutodocRules.namesAreUnique(classes);
            AutodocRules.participantsAreDeclared(classes);
            AutodocRules.expressionsParse(classes);
            AutodocRules.flowMethodsAreProxyable.check(classes);
            AutodocRules.noSelfInvocation.check(classes);
        }).doesNotThrowAnyException();
    }
}
