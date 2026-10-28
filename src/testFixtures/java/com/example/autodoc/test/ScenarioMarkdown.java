package com.example.autodoc.test;

import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Renders a scenario page: expected behavior, request, diagram, downstream calls and response. The diagram is
 * embedded as a Mermaid block, or as an image file next to the page for tools that can't render Mermaid.
 */
final class ScenarioMarkdown {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * @param downstreamCalls WireMock serve events in arrival order
     * @param stubIds         ids of the scenario's stubs in registration order, so a match can be named stubs[i]
     * @param mermaid         the sequence diagram as Mermaid text
     * @param expected        the "Expected behavior" section, or "" for none
     */
    record Report(String name, Scenario scenario, int status, List<FlowRecorder.Step> roots, String mermaid,
                  String expected, List<ServeEvent> downstreamCalls, List<UUID> stubIds, String responseBody) {
    }

    private final Map<String, String> labels;

    ScenarioMarkdown(Map<String, String> labels) {
        this.labels = labels;
    }

    /** @param image file name of the diagram image next to the page, or null to embed the Mermaid text */
    String render(Report r, String image) {
        List<FlowRecorder.Step> steps = r.roots().stream().flatMap(FlowRecorder.Step::flatten).toList();

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(r.name()).append("\n\n")
                .append(r.scenario().description()).append("\n\n")
                .append("**Status:** %d (expected %d) · **Called:** %s\n\n".formatted(
                        r.status(), r.scenario().expectedStatus(), called(r.roots(), steps)));

        sb.append(r.expected());

        sb.append("## Request\n\n");
        details(sb, r.scenario().method() + " " + r.scenario().path(), pretty(r.scenario().request()));

        sb.append("## Flow\n\n");
        if (image == null) {
            sb.append("```mermaid\n").append(r.mermaid()).append("```\n\n");
        } else {
            sb.append("![Sequence diagram for %s](%s)\n\n".formatted(r.name(), image));
        }

        sb.append("## Downstream calls\n\n");
        if (r.downstreamCalls().isEmpty()) {
            sb.append("None.\n\n");
        }
        for (ServeEvent call : r.downstreamCalls()) {
            sb.append("### %s %s → %d\n\n".formatted(
                    call.getRequest().getMethod(), call.getRequest().getUrl(), call.getResponse().getStatus()));
            sb.append(call.getWasMatched() ? "Matched `%s`.\n\n".formatted(stubName(r, call)) : "Matched no stub.\n\n");
            details(sb, "Request sent", prettyBody(call.getRequest().getBodyAsString()));
            details(sb, "Response returned", prettyBody(call.getResponse().getBodyAsString()));
        }

        List<ServeEvent> unmatched = r.downstreamCalls().stream().filter(c -> !c.getWasMatched()).toList();
        if (!unmatched.isEmpty()) {
            sb.append("## Unmatched requests\n\n")
                    .append("> **Warning:** these downstream calls matched no stub and got WireMock's 404.\n\n");
            unmatched.forEach(c -> sb.append("- %s %s\n".formatted(c.getRequest().getMethod(), c.getRequest().getUrl())));
            sb.append('\n');
        }

        sb.append("## Response\n\n");
        details(sb, "%d %s".formatted(r.status(), HttpStatus.valueOf(r.status()).getReasonPhrase()),
                prettyBody(r.responseBody()));
        return sb.toString().stripTrailing() + "\n";
    }

    /** Participants other than the one that received the request, in call order. */
    private String called(List<FlowRecorder.Step> roots, List<FlowRecorder.Step> steps) {
        if (roots.isEmpty()) {
            return "none";
        }
        String receiver = roots.get(0).participant;
        String called = steps.stream()
                .filter(s -> !s.participant.equals(receiver))
                .map(s -> labels.getOrDefault(s.participant, s.participant))
                .distinct()
                .collect(Collectors.joining(", "));
        return called.isEmpty() ? "none" : called;
    }

    private static String stubName(Report r, ServeEvent call) {
        int index = r.stubIds().indexOf(call.getStubMapping().getId());
        return index < 0 ? call.getStubMapping().getId().toString() : "stubs[%d]".formatted(index);
    }

    /** A null body renders as plain text rather than an empty code block. */
    private static void details(StringBuilder sb, String summary, String json) {
        sb.append("<details><summary>").append(summary).append("</summary>\n\n")
                .append(json == null ? "_Empty body._\n" : "```json\n" + json + "\n```\n")
                .append("\n</details>\n\n");
    }

    private static String prettyBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String trimmed = body.strip();
        return trimmed.startsWith("{") || trimmed.startsWith("[") ? pretty(JSON.readTree(trimmed)) : trimmed;
    }

    private static String pretty(JsonNode node) {
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }
}
