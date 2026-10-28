package com.example.autodoc.test;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.autodoc.test.SequenceModel.Arrow;
import com.example.autodoc.test.SequenceModel.Lane;
import com.example.autodoc.test.SequenceModel.Message;
import com.example.autodoc.test.SequenceModel.Note;
import com.example.autodoc.test.SequenceModel.Par;
import com.example.autodoc.test.SequenceModel.Participant;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

class SequenceRenderingTest {

    private static final SequenceModel MODEL = new SequenceModel(
            List.of(new Participant("UPSTREAM", "Upstream"), new Participant("APP", "App"),
                    new Participant("A", "Partner A"), new Participant("B", "Partner \"B\"")),
            List.of(new Message("UPSTREAM", "APP", "Receive request", Arrow.CALL),
                    new Message("APP", "APP", "Decide; then route", Arrow.CALL),
                    new Note(List.of("APP", "A"), "Using passport FA0000002"),
                    new Par(List.of(
                            new Lane("Call A", List.of(new Message("APP", "A", "Call A", Arrow.CALL),
                                    new Message("A", "APP", "response", Arrow.REPLY))),
                            new Lane("Call B", List.of(new Message("APP", "B", "Call B", Arrow.CALL),
                                    new Message("B", "APP", "HttpServerErrorException", Arrow.FAILED))))),
                    new Message("APP", "UPSTREAM", "502 Bad Gateway", Arrow.REPLY)));

    @Test
    void mermaidKeepsTheEstablishedSyntax() {
        assertThat(MermaidSequence.render(MODEL, "A description")).isEqualTo("""
                sequenceDiagram
                    %% A description
                    participant UPSTREAM as Upstream
                    participant APP as App
                    participant A as Partner A
                    participant B as Partner #quot;B#quot;
                    UPSTREAM->>APP: Receive request
                    APP->>APP: Decide#59; then route
                    Note over APP,A: Using passport FA0000002
                    par Call A
                        APP->>A: Call A
                        A-->>APP: response
                    and Call B
                        APP->>B: Call B
                        B--xAPP: HttpServerErrorException
                    end
                    APP-->>UPSTREAM: 502 Bad Gateway
                """);
    }

    @Test
    void svgIsWellFormedStableAndShowsEveryElement() throws Exception {
        String svg = SvgSequence.render(MODEL);

        assertThat(SvgSequence.render(MODEL)).isEqualTo(svg);
        Document doc = parse(svg);
        assertThat(texts(doc)).contains("Upstream", "App", "Partner A", "Partner \"B\"", "Receive request",
                "Decide; then route", "Using passport FA0000002", "par", "[Call A]", "[Call B]",
                "HttpServerErrorException", "502 Bad Gateway");
        assertThat(svg).contains("marker-end=\"url(#cross)\"").contains("stroke-dasharray=\"6 4\"");
        assertThat(doc.getElementsByTagName("line").getLength()).isEqualTo(4 + 6 + 1);   // lifelines, messages, lane divider
    }

    @Test
    void everyMessageLabelFitsBetweenItsLifelines() throws Exception {
        String svg = SvgSequence.render(MODEL);
        double width = Double.parseDouble(parse(svg).getDocumentElement().getAttribute("width"));

        assertThat(width).isGreaterThan(SvgText.width("HttpServerErrorException") + SvgText.width("Using passport FA0000002"));
    }

    @Test
    void graphSvgDrawsEveryStepAndEdge() throws Exception {
        var classes = new com.tngtech.archunit.core.importer.ClassFileImporter()
                .importPackages("com.example.autodoc.test.fixture");
        FlowGraph graph = new FlowGraph(classes, "com.example.autodoc.test.fixture", FlowParticipants.labels(classes));

        String svg = SvgGraph.render(graph);

        assertThat(SvgGraph.render(graph)).isEqualTo(svg);
        Document doc = parse(svg);
        assertThat(texts(doc)).contains("Receive", "Orchestrate", "Call one", "Map failure", "Backend",
                "IllegalStateException");
        assertThat(doc.getElementsByTagName("line").getLength()).isEqualTo(graph.edges().size());
    }

    private static Document parse(String svg) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<String> texts(Document doc) {
        NodeList nodes = doc.getElementsByTagName("text");
        return java.util.stream.IntStream.range(0, nodes.getLength()).mapToObj(i -> nodes.item(i).getTextContent()).toList();
    }
}
