package com.example.flowcap.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Turns expectations into sentences for business readers, for the common WireMock matchers. Any other
 * pattern is shown as-is in code formatting, so nothing is hidden.
 */
final class ExpectationText {

    private static final List<String> URL_KEYS = List.of("urlPath", "url", "urlPathPattern", "urlPattern",
            "urlPathTemplate");

    private final Glossary glossary;

    ExpectationText(Glossary glossary) {
        this.glossary = glossary;
    }

    /** The "Expected behavior" section, or "" when the scenario has no expectations. */
    String section(Expectations expectations) {
        if (expectations.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("## Expected behavior\n\n");
        for (Expectations.Call call : expectations.calls()) {
            sb.append("- **").append(endpoint(call)).append("** ").append(countPhrase(call));
            List<String> details = details(call.json());
            sb.append(call.count() != null && call.count() == 0 && !details.isEmpty() ? " with these values" : "");
            sb.append(call.why() == null ? "." : " — " + call.why() + (call.why().endsWith(".") ? "" : "."));
            sb.append('\n');
            details.forEach(d -> sb.append("  - ").append(d).append('\n'));
        }
        if (!expectations.response().isEmpty()) {
            sb.append("- **The response:**\n");
            expectations.response().forEach(c -> sb.append("  - ").append(bodyPattern(c.json())).append('\n'));
        }
        return sb.append('\n').toString();
    }

    String endpoint(Expectations.Call call) {
        JsonNode request = call.json();
        String url = URL_KEYS.stream().filter(request::has).map(k -> request.get(k).asString()).findFirst().orElse("any URL");
        String method = request.has("method") ? request.get("method").asString() : "ANY";
        return glossary.endpoint(method + " " + url);
    }

    String countPhrase(Expectations.Call call) {
        if (call.count() == null) {
            return "is called";
        }
        return switch (call.count()) {
            case 0 -> "is not called";
            case 1 -> "is called once";
            default -> "is called " + call.count() + " times";
        };
    }

    /** One line per header, query parameter and body pattern constraint. */
    private List<String> details(JsonNode request) {
        List<String> details = new ArrayList<>();
        namedValues(request.path("headers"), "Header", details);
        namedValues(request.path("queryParameters"), "Query parameter", details);
        request.path("bodyPatterns").forEach(p -> details.add(bodyPattern(p)));
        return details;
    }

    private static void namedValues(JsonNode values, String kind, List<String> details) {
        for (Map.Entry<String, JsonNode> e : values.properties()) {
            JsonNode pattern = e.getValue();
            if (pattern.size() == 1 && pattern.has("equalTo")) {
                details.add("%s %s is %s".formatted(kind, e.getKey(), pattern.get("equalTo").asString()));
            } else if (pattern.size() == 1 && pattern.path("absent").asBoolean(false)) {
                details.add("%s %s is not sent".formatted(kind, e.getKey()));
            } else {
                details.add("%s %s matches `%s`".formatted(kind, e.getKey(), pattern));
            }
        }
    }

    /** A WireMock body pattern as a sentence. */
    String bodyPattern(JsonNode pattern) {
        JsonNode jsonPath = pattern.get("matchesJsonPath");
        if (pattern.size() == 1 && jsonPath != null) {
            if (jsonPath.isString()) {
                return glossary.field(jsonPath.asString()) + " is provided";
            }
            String expression = jsonPath.path("expression").asString("");
            if (jsonPath.size() == 2 && jsonPath.has("equalTo")) {
                String value = jsonPath.get("equalTo").asString();
                if (expression.endsWith(".length()")) {
                    String path = expression.substring(0, expression.length() - ".length()".length());
                    return "%s has %s %s".formatted(glossary.field(path), value, "1".equals(value) ? "entry" : "entries");
                }
                return glossary.field(expression) + " is " + value;
            }
            if (jsonPath.size() == 2 && jsonPath.path("absent").asBoolean(false)) {
                return glossary.field(expression) + " is not provided";
            }
        }
        return "Matches `" + pattern + "`";
    }
}
