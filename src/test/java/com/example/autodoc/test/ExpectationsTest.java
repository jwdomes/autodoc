package com.example.autodoc.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import tools.jackson.databind.json.JsonMapper;

/** Checks expectations against a real WireMock request log, so WireMock's own matching is what's tested. */
class ExpectationsTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
            .build();

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String RESPONSE = """
            {"personId": "P-1", "results": [{"partner": "PARTNER_A"}, {"partner": "PARTNER_C"}]}""";

    private final ExpectationText text = new ExpectationText(new Glossary(
            Map.of("POST /partner-a/matches", "Partner A match search"), Map.of("$.personId", "Person ID")));

    @BeforeEach
    void callPartnerATwiceAndPartnerCOnce() throws Exception {
        wireMock.resetAll();
        wireMock.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
        post("/partner-a/matches", "{\"personId\": \"900000002\", \"documentNumber\": \"FA0000002\"}");
        post("/partner-a/matches", "{\"personId\": \"900000003\", \"documentNumber\": \"FA0000003\"}");
        post("/partner-c/matches", "{\"personIdentifier\": \"900000002\"}");
    }

    private static void post(String path, String body) throws Exception {
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build().send(
                HttpRequest.newBuilder(URI.create(wireMock.baseUrl() + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.discarding());
    }

    private static List<LoggedRequest> log() {
        return wireMock.getAllServeEvents().reversed().stream().map(ServeEvent::getRequest).toList();
    }

    private List<String> failures(String expect) {
        return Expectations.parse(JSON.readTree(expect), "test").failures(log(), RESPONSE, text);
    }

    @Test
    void passingCallAndResponseExpectations() {
        assertThat(failures("""
                {"calls": [
                  {"request": {"method": "POST", "urlPath": "/partner-a/matches",
                     "bodyPatterns": [{"matchesJsonPath": {"expression": "$.personId", "equalTo": "900000002"}},
                                      {"matchesJsonPath": "$.documentNumber"}]}, "count": 1},
                  {"request": {"method": "POST", "urlPath": "/partner-a/matches"}, "count": 2},
                  {"request": {"method": "POST", "urlPath": "/partner-b/matches"}, "count": 0},
                  {"request": {"method": "POST", "urlPath": "/partner-c/matches",
                     "bodyPatterns": [{"matchesJsonPath": {"expression": "$.firstName", "absent": true}}]}}
                 ],
                 "response": {"bodyPatterns": [
                   {"matchesJsonPath": {"expression": "$.personId", "equalTo": "P-1"}},
                   {"matchesJsonPath": {"expression": "$.results.length()", "equalTo": "2"}},
                   {"matchesJsonPath": {"expression": "$.results[0].partner", "equalTo": "PARTNER_A"}},
                   {"matchesJsonPath": {"expression": "$.nickname", "absent": true}}]}}
                """)).isEmpty();
    }

    @Test
    void reportsEveryFailureWithTheClosestRequest() {
        List<String> failures = failures("""
                {"calls": [
                  {"request": {"method": "POST", "urlPath": "/partner-a/matches",
                     "bodyPatterns": [{"matchesJsonPath": {"expression": "$.personId", "equalTo": "123"}}]}},
                  {"request": {"method": "POST", "urlPath": "/partner-c/matches"}, "count": 0},
                  {"request": {"method": "POST", "urlPath": "/partner-a/matches"}, "count": 1},
                  {"request": {"method": "POST", "urlPath": "/partner-c/matches",
                     "bodyPatterns": [{"matchesJsonPath": {"expression": "$.personIdentifier", "absent": true}}]}}
                 ],
                 "response": {"bodyPatterns": [
                   {"matchesJsonPath": {"expression": "$.results.length()", "equalTo": "3"}}]}}
                """);

        assertThat(failures).hasSize(6);
        assertThat(failures.get(0)).startsWith("Partner A match search: expected to be called, but it was called 0 times")
                .contains("Closest request:").contains("$.personId");
        assertThat(failures.get(1)).isEqualTo("`POST /partner-c/matches`: expected not to be called, but it was called 1 time");
        assertThat(failures.get(2)).isEqualTo("Partner A match search: expected to be called once, but it was called 2 times");
        assertThat(failures.get(3)).startsWith("`POST /partner-c/matches`: expected to be called, but it was called 0 times");
        assertThat(failures.get(4)).isEqualTo("The response: expected \"Results has 3 entries\"");
        assertThat(failures.get(5)).startsWith("Response body was: {\"personId\"");
    }

    @Test
    void aMalformedPatternNamesTheScenarioAndEntry() {
        assertThatThrownBy(() -> Expectations.parse(JSON.readTree("""
                {"calls": [{"request": {"method": "POST", "bodyPatterns": [{"noSuchMatcher": "x"}]}}]}
                """), "my-scenario"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Scenario my-scenario: expect.calls[0].request isn't a valid WireMock pattern");
    }

    @Test
    void aCallWithoutARequestPatternFails() {
        assertThatThrownBy(() -> Expectations.parse(JSON.readTree("{\"calls\": [{\"count\": 1}]}"), "my-scenario"))
                .hasMessage("Scenario my-scenario: expect.calls[0] needs a \"request\" pattern");
    }
}
