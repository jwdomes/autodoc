package com.example.autodoc.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * One scenario file: the request to send, the status the app must return, the WireMock stubs that stand
 * in for downstream systems, any configuration the app must run with ({@code properties}, e.g.
 * {@code {"id-provider.use-new": "true"}}), and optional expectations ({@code expect}, see
 * {@link Expectations}). The file name (without .json) is the scenario's name.
 */
public record Scenario(String description, String method, String path, Map<String, String> properties,
                       JsonNode request, int expectedStatus, List<JsonNode> stubs,
                       JsonNode expect) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public Scenario {
        if (description == null || method == null || path == null || request == null || expectedStatus == 0) {
            throw new IllegalArgumentException(
                    "Scenario needs description, method, path, request and expectedStatus");
        }
        stubs = stubs == null ? List.of() : List.copyOf(stubs);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    public static Scenario load(Path dir, String name) throws IOException {
        return JSON.readValue(Files.readString(dir.resolve(name + ".json")), Scenario.class);
    }

    /** All scenario names in the directory, sorted. */
    public static Stream<String> names(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - ".json".length()))
                    .sorted().toList().stream();
        }
    }

    /**
     * Names of the scenarios whose {@code properties} are exactly {@code properties}; for the @MethodSource of a
     * scenario test whose context runs with those properties. Use {@code Map.of()} for the default context.
     */
    public static Stream<String> names(Path dir, Map<String, String> properties) throws IOException {
        List<String> matching = new ArrayList<>();
        for (String name : names(dir).toList()) {
            if (load(dir, name).properties().equals(properties)) {
                matching.add(name);
            }
        }
        return matching.stream();
    }
}
