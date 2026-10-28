package com.example.autodoc.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Runs one scenario against the app with its stubs loaded into WireMock, checks the status and the
 * scenario's expectations, and writes its docs in each enabled {@link DocFormat}. Create once per test class;
 * call {@link #run} from a parameterized test whose context has autodoc.observations.enabled=true and
 * {@link FlowRecorderConfiguration}.
 */
public class ScenarioRunner {

    private final AutodocSettings settings;
    private final WireMockExtension wireMock;
    private final DocWriter writer;
    private final SequenceModelBuilder diagrams;
    private final ScenarioMarkdown pages;
    private final ExpectationText expectationText;

    public ScenarioRunner(AutodocSettings settings, WireMockExtension wireMock) {
        this(settings, wireMock, DocWriter.fromSystemProperties());
    }

    public ScenarioRunner(AutodocSettings settings, WireMockExtension wireMock, DocWriter writer) {
        this.settings = settings;
        this.wireMock = wireMock;
        this.writer = writer;
        Map<String, String> labels = FlowParticipants.labels(settings.importClasses());
        this.diagrams = new SequenceModelBuilder(labels, settings);
        this.pages = new ScenarioMarkdown(labels);
        try {
            this.expectationText = new ExpectationText(Glossary.load(settings.glossaryFile()));
        } catch (IOException e) {
            throw new UncheckedIOException("Can't read the glossary " + settings.glossaryFile(), e);
        }
    }

    public void run(String name, MockMvc mvc, FlowRecorder recorder) throws Exception {
        Scenario scenario = Scenario.load(settings.scenariosDir(), name);
        scenario.properties().forEach((key, value) -> assertEquals(value, recorder.properties().getProperty(key),
                "Scenario " + name + " needs " + key + "=" + value + "; run it from a test whose context sets it"));
        Expectations expectations = Expectations.parse(scenario.expect(), name);

        wireMock.resetAll();
        recorder.clear();
        List<UUID> stubIds = new ArrayList<>();
        for (JsonNode stub : scenario.stubs()) {
            StubMapping mapping = StubMapping.buildFrom(stub.toString());
            stubIds.add(mapping.getId());
            wireMock.getRuntimeInfo().getWireMock().register(mapping);
        }

        MvcResult result = mvc.perform(request(HttpMethod.valueOf(scenario.method()), scenario.path())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scenario.request().toString()))
                .andReturn();
        MockHttpServletResponse response = result.getResponse();
        List<FlowRecorder.Step> roots = recorder.roots();
        List<String> problems = roots.stream().flatMap(FlowRecorder.Step::flatten)
                .map(FlowRecorder.Step::problem).filter(Objects::nonNull).toList();
        assertTrue(problems.isEmpty(), "Scenario " + name + " has @FlowStep notes that failed: " + problems);
        assertEquals(scenario.expectedStatus(), response.getStatus(), "Status for scenario " + name);

        List<ServeEvent> calls = new ArrayList<>(wireMock.getAllServeEvents());
        Collections.reverse(calls);   // WireMock lists newest first
        List<LoggedRequest> requests = calls.stream().map(ServeEvent::getRequest).toList();
        String body = response.getContentAsString();
        List<String> failures = expectations.failures(requests, body, expectationText);
        assertTrue(failures.isEmpty(), "Scenario " + name + " expectations failed:\n- " + String.join("\n- ", failures));

        SequenceModel model = diagrams.build(roots, response.getStatus(),
                scenario.method() + " " + scenario.path(), result.getResolvedException());
        String mermaid = MermaidSequence.render(model, scenario.description());
        ScenarioMarkdown.Report report = new ScenarioMarkdown.Report(name, scenario, response.getStatus(), roots,
                mermaid, expectationText.section(expectations), calls, stubIds, body);

        Path docs = settings.docsDir();
        if (writer.writes(DocFormat.MERMAID)) {
            writer.write(docs.resolve("flows/" + name + ".mmd"), mermaid);
        }
        if (writer.writes(DocFormat.MARKDOWN)) {
            writer.write(docs.resolve("flows/" + name + ".md"), pages.render(report, null));
        }
        if (writer.writes(DocFormat.MARKDOWN_IMAGE)) {
            Path dir = docs.resolve(StaticAutodoc.IMAGE_DIR).resolve("flows");
            writer.write(dir.resolve(name + ".svg"), SvgSequence.render(model));
            writer.write(dir.resolve(name + ".md"), pages.render(report, name + ".svg"));
        }
    }
}
