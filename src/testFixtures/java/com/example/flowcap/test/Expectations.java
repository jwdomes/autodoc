package com.example.flowcap.test;

import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.matching.ContentPattern;
import com.github.tomakehurst.wiremock.matching.RequestPattern;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.verification.diff.Diff;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * A scenario's {@code expect} block, in WireMock's own matching syntax: each call is a WireMock request
 * pattern (as in a stub's {@code request}) with an optional exact {@code count} and a {@code why}; the
 * response is checked against a WireMock {@code bodyPatterns} list.
 */
record Expectations(List<Call> calls, List<Check> response) {

    /** @param count exact number of matching calls; null means at least once */
    record Call(JsonNode json, RequestPattern pattern, Integer count, String why) {
    }

    record Check(JsonNode json, StringValuePattern pattern) {
    }

    static final Expectations NONE = new Expectations(List.of(), List.of());

    static Expectations parse(JsonNode expect, String scenario) {
        if (expect == null || expect.isNull()) {
            return NONE;
        }
        List<Call> calls = new ArrayList<>();
        JsonNode callNodes = expect.path("calls");
        for (int i = 0; i < callNodes.size(); i++) {
            JsonNode call = callNodes.get(i);
            JsonNode request = call.path("request");
            if (!request.isObject()) {
                throw new IllegalArgumentException("Scenario %s: expect.calls[%d] needs a \"request\" pattern".formatted(scenario, i));
            }
            calls.add(new Call(request, read(request.toString(), scenario, "expect.calls[" + i + "].request"),
                    call.has("count") ? call.get("count").asInt() : null,
                    call.has("why") ? call.get("why").asString() : null));
        }
        List<Check> response = new ArrayList<>();
        JsonNode bodyPatterns = expect.path("response").path("bodyPatterns");
        if (!bodyPatterns.isMissingNode()) {
            List<ContentPattern<?>> patterns = read("{\"bodyPatterns\":" + bodyPatterns + "}", scenario,
                    "expect.response.bodyPatterns").getBodyPatterns();
            for (int i = 0; i < patterns.size(); i++) {
                if (!(patterns.get(i) instanceof StringValuePattern pattern)) {
                    throw new IllegalArgumentException("Scenario %s: expect.response.bodyPatterns[%d] must match text"
                            .formatted(scenario, i));
                }
                response.add(new Check(bodyPatterns.get(i), pattern));
            }
        }
        return new Expectations(calls, response);
    }

    private static RequestPattern read(String json, String scenario, String where) {
        try {
            return Json.read(json, RequestPattern.class);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Scenario %s: %s isn't a valid WireMock pattern: %s"
                    .formatted(scenario, where, e.getMessage()), e);
        }
    }

    boolean isEmpty() {
        return calls.isEmpty() && response.isEmpty();
    }

    /** Every failed expectation, described for a reader; empty when all hold. */
    List<String> failures(List<? extends Request> downstream, String responseBody, ExpectationText text) {
        List<String> failures = new ArrayList<>();
        for (Call call : calls) {
            long actual = downstream.stream().filter(r -> call.pattern().match(r).isExactMatch()).count();
            boolean holds = call.count() == null ? actual > 0 : actual == call.count();
            if (!holds) {
                String expected = text.countPhrase(call).replaceFirst("^is not ", "not to be ").replaceFirst("^is ", "to be ");
                StringBuilder failure = new StringBuilder("%s: expected %s, but it was called %d time%s".formatted(
                        text.endpoint(call), expected, actual, actual == 1 ? "" : "s"));
                if (actual == 0 && !downstream.isEmpty()) {
                    Request closest = downstream.stream()
                            .min(Comparator.comparingDouble(r -> call.pattern().match(r).getDistance()))
                            .orElseThrow();
                    failure.append("\nClosest request:\n").append(new Diff(call.pattern(), closest));
                }
                failures.add(failure.toString());
            }
        }
        for (Check check : response) {
            if (!check.pattern().match(responseBody).isExactMatch()) {
                failures.add("The response: expected \"%s\"".formatted(text.bodyPattern(check.json())));
            }
        }
        if (failures.stream().anyMatch(f -> f.startsWith("The response"))) {
            failures.add("Response body was: " + responseBody);
        }
        return failures;
    }
}
