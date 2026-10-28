package com.example.autodoc.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ExpectationTextTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Glossary glossary = new Glossary(
            Map.of("POST /partner-a/matches", "Partner A match search"),
            Map.of("$.personId", "Person ID", "$.results[*].partner", "Partner"));
    private final ExpectationText text = new ExpectationText(glossary);

    private String section(String expect) {
        return text.section(Expectations.parse(JSON.readTree(expect), "test"));
    }

    @Test
    void rendersCallsAndTheResponseAsSentences() {
        assertThat(section("""
                {"calls": [
                  {"request": {"method": "POST", "urlPath": "/partner-a/matches",
                     "headers": {"X-Request-Id": {"equalTo": "r-1"}},
                     "bodyPatterns": [{"matchesJsonPath": {"expression": "$.personId", "equalTo": "900000002"}},
                                      {"matchesJsonPath": "$.documentNumber"},
                                      {"matchesJsonPath": {"expression": "$.firstName", "absent": true}}]},
                   "why": "Partner A needs an SSN and a valid passport"},
                  {"request": {"method": "POST", "urlPath": "/partner-b/matches"}, "count": 0,
                   "why": "Partner B needs a PNumber."},
                  {"request": {"method": "POST", "urlPath": "/partner-c/matches",
                     "bodyPatterns": [{"matchesJsonPath": {"expression": "$.personIdentifier", "equalTo": "1"}}]},
                   "count": 0},
                  {"request": {"method": "GET", "url": "/status"}, "count": 1},
                  {"request": {"method": "GET", "url": "/status?x=1"}, "count": 3}
                 ],
                 "response": {"bodyPatterns": [
                   {"matchesJsonPath": {"expression": "$.results.length()", "equalTo": "2"}},
                   {"matchesJsonPath": {"expression": "$.results.length()", "equalTo": "1"}},
                   {"matchesJsonPath": {"expression": "$.results[0].partner", "equalTo": "PARTNER_A"}},
                   {"matchesJsonPath": {"expression": "$.results[1].score", "equalTo": "0.5"}},
                   {"matchesJsonPath": {"expression": "$.results[1]", "absent": true}},
                   {"matchesJsonPath": "$[?(@.results.size() > 0)]"},
                   {"contains": "PARTNER"}]}}
                """)).isEqualTo("""
                ## Expected behavior

                - **Partner A match search** is called — Partner A needs an SSN and a valid passport.
                  - Header X-Request-Id is r-1
                  - Person ID is 900000002
                  - Document number is provided
                  - First name is not provided
                - **`POST /partner-b/matches`** is not called — Partner B needs a PNumber.
                - **`POST /partner-c/matches`** is not called with these values.
                  - Person identifier is 1
                - **`GET /status`** is called once.
                - **`GET /status?x=1`** is called 3 times.
                - **The response:**
                  - Results has 2 entries
                  - Results has 1 entry
                  - Partner (Results #1) is PARTNER_A
                  - Score (Results #2) is 0.5
                  - Results #2 is not provided
                  - `$[?(@.results.size() > 0)]` is provided
                  - Matches `{"contains":"PARTNER"}`

                """);
    }

    @Test
    void noExpectationsMeansNoSection() {
        assertThat(text.section(Expectations.parse(null, "test"))).isEmpty();
    }

    @Test
    void tidiesFieldNamesWithoutALabel() {
        assertThat(Glossary.tidy("documentNumber")).isEqualTo("Document number");
        assertThat(Glossary.tidy("birth_date")).isEqualTo("Birth date");
        assertThat(new Glossary(null, null).field("$.person.lastName")).isEqualTo("Last name");
    }
}
