package com.example.router;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.listFeeder;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Replays every flow scenario as load, with all scenarios' stubs loaded into one WireMock. */
public class FlowScenariosSimulation extends Simulation {

    private static final Path SCENARIOS = Path.of(System.getProperty("autodoc.scenarios.dir", "src/test/resources/scenarios"));
    private static final String APP_URL = env("APP_URL", "http://localhost:8090");
    private static final String WIREMOCK_URL = env("WIREMOCK_URL", "http://localhost:8080");
    private static final int PARTNER_DELAY_MS = Integer.parseInt(env("PARTNER_DELAY_MS", "0"));
    private static final int PEAK_USERS_PER_SEC = Integer.parseInt(env("PEAK_USERS_PER_SEC", "20"));
    private static final int RAMP_SECONDS = Integer.parseInt(env("RAMP_SECONDS", "60"));
    // One running app has one configuration: load only the scenarios declaring exactly these properties,
    // e.g. GATLING_PROPERTIES=id-provider.use-new=true for an app started with that setting.
    private static final Map<String, String> PROPERTIES = parseProperties(env("GATLING_PROPERTIES", ""));
    private static final ObjectMapper JSON = new ObjectMapper();   // Jackson 2, bundled with Gatling

    private static final List<Map<String, Object>> SCENARIO_DATA = loadScenarios();

    /** Load every scenario's stubs into WireMock once, optionally adding partner latency. */
    @Override
    public void before() {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        post(client, "/__admin/reset", "");
        for (Map<String, Object> s : SCENARIO_DATA) {
            for (JsonNode stub : (JsonNode) s.get("stubs")) {
                ObjectNode copy = stub.deepCopy();
                if (PARTNER_DELAY_MS > 0) {
                    ((ObjectNode) copy.path("response")).put("fixedDelayMilliseconds", PARTNER_DELAY_MS);
                }
                post(client, "/__admin/mappings", copy.toString());
            }
        }
    }

    private final ScenarioBuilder flows = scenario("Flow scenarios")
            .feed(listFeeder(SCENARIO_DATA).circular())
            .exec(http("#{name}")
                    .httpRequest("#{method}", "#{path}")
                    .body(StringBody("#{body}"))
                    .check(status().is(session -> session.getInt("status"))));

    {
        setUp(flows.injectOpen(rampUsersPerSec(1).to(PEAK_USERS_PER_SEC).during(Duration.ofSeconds(RAMP_SECONDS))))
                .protocols(http.baseUrl(APP_URL).contentTypeHeader("application/json"))
                .assertions(
                        global().responseTime().percentile(95.0).lt(800),
                        global().failedRequests().percent().lt(1.0));
    }

    private static List<Map<String, Object>> loadScenarios() {
        try (Stream<Path> files = Files.list(SCENARIOS)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted()
                    .filter(p -> properties(read(p)).equals(PROPERTIES))
                    .map(p -> {
                        JsonNode s = read(p);
                        return Map.<String, Object>of(
                                "name", p.getFileName().toString().replace(".json", ""),
                                "method", s.get("method").asText(),
                                "path", s.get("path").asText(),
                                "body", s.get("request").toString(),
                                "status", s.get("expectedStatus").asInt(),
                                "stubs", s.get("stubs"));
                    })
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, String> properties(JsonNode scenario) {
        Map<String, String> properties = new HashMap<>();
        scenario.path("properties").properties().forEach(e -> properties.put(e.getKey(), e.getValue().asText()));
        return properties;
    }

    private static Map<String, String> parseProperties(String spec) {
        Map<String, String> properties = new HashMap<>();
        for (String pair : spec.split(",")) {
            if (!pair.isBlank()) {
                String[] keyValue = pair.split("=", 2);
                properties.put(keyValue[0].strip(), keyValue[1].strip());
            }
        }
        return properties;
    }

    private static JsonNode read(Path p) {
        try {
            return JSON.readTree(p.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void post(HttpClient client, String path, String body) {
        try {
            HttpResponse<String> r = client.send(HttpRequest.newBuilder(URI.create(WIREMOCK_URL + path))
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() >= 300) {
                throw new IllegalStateException(path + " -> " + r.statusCode() + ": " + r.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("WireMock admin call failed: " + path, e);
        }
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }
}
