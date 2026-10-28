package com.example.autodoc.test;

import com.example.autodoc.FlowStep;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The static flow graph, built from bytecode without starting the app: every step, with an edge to each
 * step it calls, directly or through unannotated helpers. Exception handlers hang off the controller
 * steps as dashed edges labelled with their exceptions.
 */
public final class FlowGraph {

    public record Node(String id, String text, String participant, JavaMethod method) {
    }

    /** @param label null for call edges; the exception names for handler edges, which are dashed */
    public record Edge(String from, String to, String label) implements Comparable<Edge> {

        boolean dashed() {
            return label != null;
        }

        @Override
        public int compareTo(Edge other) {
            return Comparator.comparing(Edge::from).thenComparing(Edge::to)
                    .thenComparing(Edge::dashed).compare(this, other);
        }
    }

    private final String basePackage;
    private final Map<String, String> labels;
    private final Map<JavaMethod, Node> nodes = new HashMap<>();

    public FlowGraph(JavaClasses classes, String basePackage, Map<String, String> labels) {
        this.basePackage = basePackage;
        this.labels = labels;
        for (JavaClass c : classes) {
            for (JavaMethod m : c.getMethods()) {
                m.tryGetAnnotationOfType(FlowStep.class).ifPresent(fs -> nodes.put(m, new Node(
                        FlowIds.slug(fs.action()), fs.action(), fs.participant(), m)));
            }
        }
    }

    public List<Node> nodes() {
        return nodes.values().stream()
                .sorted(Comparator.comparing(Node::participant).thenComparing(Node::text))
                .toList();
    }

    public Set<Edge> edges() {
        Set<Edge> edges = new TreeSet<>();
        nodes.forEach((m, from) -> reachable(m).forEach(to -> edges.add(new Edge(from.id(), to.id(), null))));
        List<Node> controllers = stepsInClassesAnnotatedWith(RestController.class);
        for (Node handler : stepsInClassesAnnotatedWith(RestControllerAdvice.class)) {
            String exceptions = handler.method().tryGetAnnotationOfType(ExceptionHandler.class)
                    .map(eh -> Arrays.stream(eh.value()).map(Class::getSimpleName).collect(Collectors.joining(", ")))
                    .orElse("");
            controllers.forEach(c -> edges.add(new Edge(c.id(), handler.id(), exceptions)));
        }
        return edges;
    }

    public String label(String participant) {
        String label = labels.get(participant);
        if (label == null) {
            throw new IllegalStateException("No @FlowParticipant declares id " + participant);
        }
        return label;
    }

    public String mermaid() {
        StringBuilder sb = new StringBuilder("flowchart TD\n");
        Map<String, List<Node>> byParticipant = nodes().stream()
                .collect(Collectors.groupingBy(Node::participant, TreeMap::new, Collectors.toList()));
        byParticipant.forEach((participant, members) -> {
            sb.append("    subgraph %s[\"%s\"]\n".formatted(participant, FlowIds.escape(label(participant))));
            members.stream().sorted(Comparator.comparing(Node::id))
                    .forEach(n -> sb.append("        %s[\"%s\"]\n".formatted(n.id(), FlowIds.escape(n.text()))));
            sb.append("    end\n");
        });
        Set<String> lines = new TreeSet<>();
        for (Edge e : edges()) {
            lines.add(e.dashed()
                    ? "    %s -.->|\"%s\"| %s".formatted(e.from(), FlowIds.escape(e.label()), e.to())
                    : "    %s --> %s".formatted(e.from(), e.to()));
        }
        lines.forEach(line -> sb.append(line).append('\n'));
        return sb.toString();
    }

    private List<Node> stepsInClassesAnnotatedWith(Class<? extends Annotation> annotation) {
        return nodes.values().stream()
                .filter(n -> n.method().getOwner().isAnnotatedWith(annotation))
                .toList();
    }

    /** Follow calls through unannotated helpers until reaching other steps. */
    private Set<Node> reachable(JavaMethod start) {
        Set<Node> found = new HashSet<>();
        Set<JavaMethod> visited = new HashSet<>(Set.of(start));
        Deque<JavaMethod> stack = new ArrayDeque<>(List.of(start));
        while (!stack.isEmpty()) {
            for (JavaMethodCall call : stack.pop().getMethodCallsFromSelf()) {
                for (JavaMethod target : resolve(call)) {
                    if (target.equals(start)) {
                        continue;
                    }
                    Node node = nodes.get(target);
                    if (node != null) {
                        found.add(node);                 // stop: that step owns its edges
                    } else if (visited.add(target)) {
                        stack.push(target);              // traverse helper
                    }
                }
            }
        }
        return found;
    }

    private List<JavaMethod> resolve(JavaMethodCall call) {
        return call.getTarget().resolveMember().stream()
                .filter(t -> t.getOwner().getPackageName().startsWith(basePackage))
                .flatMap(this::withImplementations)
                .toList();
    }

    /** Calls through an injected interface resolve to the interface; fan out to implementations. */
    private Stream<JavaMethod> withImplementations(JavaMethod target) {
        if (!target.getOwner().isInterface()) {
            return Stream.of(target);
        }
        String[] params = target.getRawParameterTypes().stream().map(JavaClass::getName).toArray(String[]::new);
        return Stream.concat(Stream.of(target), target.getOwner().getAllSubclasses().stream()
                .flatMap(impl -> impl.tryGetMethod(target.getName(), params).stream()));
    }
}
