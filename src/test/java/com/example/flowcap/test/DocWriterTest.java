package com.example.flowcap.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocWriterTest {

    @TempDir
    Path dir;

    @Test
    void parsesFormatListsAndDefaultsToAll() {
        assertThat(DocFormat.parse(null)).isEqualTo(EnumSet.allOf(DocFormat.class));
        assertThat(DocFormat.parse(" ")).isEqualTo(EnumSet.allOf(DocFormat.class));
        assertThat(DocFormat.parse("mermaid, Markdown-Image"))
                .isEqualTo(EnumSet.of(DocFormat.MERMAID, DocFormat.MARKDOWN_IMAGE));
    }

    @Test
    void anUnknownFormatFailsListingTheValidOnes() {
        assertThatThrownBy(() -> DocFormat.parse("markdown,pdf"))
                .hasMessage("Unknown flowcap.docs.formats entry \"pdf\"; valid: markdown, markdown-image, mermaid");
    }

    @Test
    void writesByDefault() throws Exception {
        Path doc = dir.resolve("flows/a.md");
        new DocWriter(EnumSet.allOf(DocFormat.class), false).write(doc, "new");

        assertThat(doc).hasContent("new");
    }

    @Test
    void verifyFailsOnAStaleDocAndWritesNothing() throws Exception {
        Path doc = dir.resolve("a.md");
        Files.writeString(doc, "committed");
        DocWriter verifier = new DocWriter(EnumSet.allOf(DocFormat.class), true);

        assertThatThrownBy(() -> verifier.write(doc, "changed")).hasMessageContaining("Flow docs out of date: " + doc);
        assertThat(doc).hasContent("committed");
        verifier.write(doc, "committed");
    }

    @Test
    void verifyFailsOnAMissingDoc() {
        DocWriter verifier = new DocWriter(EnumSet.allOf(DocFormat.class), true);

        assertThatThrownBy(() -> verifier.write(dir.resolve("missing.md"), "x")).hasMessageContaining("out of date");
    }
}
