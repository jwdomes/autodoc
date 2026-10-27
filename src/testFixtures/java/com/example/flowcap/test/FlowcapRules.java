package com.example.flowcap.test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.flowcap.FlowStep;
import com.example.flowcap.boot.FlowExpressions;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.PackageMatcher;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.springframework.expression.ParseException;

/**
 * Rules that keep the annotations honest. Include them with
 * {@code @ArchTest static final ArchTests flowcap = ArchTests.in(FlowcapRules.class);} in a class annotated
 * {@code @AnalyzeClasses(packages = "<base package>", importOptions = ImportOption.DoNotIncludeTests.class)}.
 */
public final class FlowcapRules {

    private static final Set<String> HTTP_CLIENTS = Set.of(
            "org.springframework.web.client.RestClient",
            "org.springframework.web.client.RestTemplate",
            "org.springframework.web.reactive.function.client.WebClient");

    /** A downstream call that isn't a step would be missing from every diagram. */
    @ArchTest
    public static final ArchRule httpCallsAreSteps =
            methods().that(callAnHttpClient()).should().beAnnotatedWith(FlowStep.class)
                    .allowEmptyShould(true);   // apps whose HTTP calls all live in a client library have none

    /**
     * Calls to downstream client interfaces the app doesn't annotate (e.g. a client library) must be made from
     * a step, so every call shows in the diagrams. Only interface methods count: reading the client's data
     * types isn't a call. Not an {@code @ArchTest} itself, since only the app knows its client packages; add
     * it as {@code @ArchTest static final ArchRule clients = downstreamCallsAreSteps("com.acme.clients..");}
     *
     * @param packages ArchUnit package patterns, e.g. {@code "com.acme.clients.."}
     */
    public static ArchRule downstreamCallsAreSteps(String... packages) {
        return methods().that(callAnInterfaceIn(packages)).should().beAnnotatedWith(FlowStep.class);
    }

    @ArchTest
    public static final ArchRule flowMethodsAreProxyable =
            methods().that().areAnnotatedWith(FlowStep.class)
                    .should().notHaveModifier(JavaModifier.PRIVATE)
                    .andShould().notHaveModifier(JavaModifier.FINAL)
                    .andShould().notHaveModifier(JavaModifier.STATIC);

    @ArchTest
    public static final ArchRule noSelfInvocation =
            methods().that().areAnnotatedWith(FlowStep.class)
                    .should(notBeCalledFromOwnClass());

    /** Steps are nodes in the static graph, identified by their action. */
    @ArchTest
    public static void namesAreUnique(JavaClasses classes) {
        Map<String, List<String>> bySlug = new TreeMap<>();
        stepMethods(classes).forEach(m -> bySlug
                .computeIfAbsent(FlowIds.slug(m.getAnnotationOfType(FlowStep.class).action()), k -> new ArrayList<>())
                .add(m.getFullName()));
        bySlug.values().removeIf(methods -> methods.size() < 2);
        assertTrue(bySlug.isEmpty(), "Duplicate step actions: " + bySlug);
    }

    @ArchTest
    public static void participantsAreDeclared(JavaClasses classes) {
        Map<String, List<String>> declared = new TreeMap<>();
        FlowParticipants.declarations(classes).forEach(d ->
                declared.computeIfAbsent(d.id(), k -> new ArrayList<>()).add(d.field()));
        List<String> duplicates = declared.entrySet().stream()
                .filter(e -> e.getValue().size() > 1).map(e -> e.getKey() + " " + e.getValue()).toList();
        List<String> undeclared = stepMethods(classes)
                .filter(m -> !declared.containsKey(participant(m)))
                .map(m -> m.getFullName() + " uses \"" + participant(m) + "\"")
                .toList();
        assertTrue(duplicates.isEmpty(), "Participant ids declared more than once: " + duplicates);
        assertTrue(undeclared.isEmpty(), "Participants with no @FlowParticipant constant: " + undeclared);
    }

    /** Catches note typos on paths no scenario exercises. */
    @ArchTest
    public static void expressionsParse(JavaClasses classes) {
        List<String> failures = new ArrayList<>();
        stepMethods(classes).forEach(m -> {
            String note = m.getAnnotationOfType(FlowStep.class).note();
            if (!note.isEmpty()) {
                try {
                    FlowExpressions.parseNote(note);
                } catch (ParseException e) {
                    failures.add(m.getFullName() + " note: " + e.getMessage());
                }
            }
        });
        assertTrue(failures.isEmpty(), "Unparseable @FlowStep notes: " + failures);
    }

    private static DescribedPredicate<JavaMethod> callAnHttpClient() {
        return DescribedPredicate.describe("call RestClient, RestTemplate or WebClient", method ->
                method.getMethodCallsFromSelf().stream()
                        .anyMatch(call -> HTTP_CLIENTS.contains(call.getTargetOwner().getName().split("\\$")[0])));
    }

    private static DescribedPredicate<JavaMethod> callAnInterfaceIn(String... packages) {
        return DescribedPredicate.describe("call an interface in " + String.join(", ", packages), method ->
                method.getMethodCallsFromSelf().stream()
                        .filter(call -> call.getTargetOwner().isInterface())
                        .anyMatch(call -> Arrays.stream(packages)
                                .anyMatch(p -> PackageMatcher.of(p).matches(call.getTargetOwner().getPackageName()))));
    }

    private static ArchCondition<JavaMethod> notBeCalledFromOwnClass() {
        return new ArchCondition<>("not be called from its own class") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                method.getCallsOfSelf().stream()
                        .filter(call -> call.getOriginOwner().equals(method.getOwner()))
                        .forEach(call -> events.add(SimpleConditionEvent.violated(method,
                                "Self-invocation bypasses the flow interceptor: " + call.getDescription())));
            }
        };
    }

    private static Stream<JavaMethod> stepMethods(JavaClasses classes) {
        return classes.stream()
                .flatMap(c -> c.getMethods().stream())
                .filter(m -> m.isAnnotatedWith(FlowStep.class));
    }

    private static String participant(JavaMethod m) {
        return m.getAnnotationOfType(FlowStep.class).participant();
    }

    private FlowcapRules() {
    }
}
