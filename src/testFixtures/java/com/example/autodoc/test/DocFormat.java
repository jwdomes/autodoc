package com.example.autodoc.test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** The kinds of doc autodoc writes, chosen with the autodoc.docs.formats system property. */
public enum DocFormat {

    /** Markdown pages with the diagram embedded as a Mermaid block. */
    MARKDOWN("markdown"),
    /** Markdown pages with the diagram embedded as an SVG image, under markdown-image/, for Confluence. */
    MARKDOWN_IMAGE("markdown-image"),
    /** The bare Mermaid diagrams (.mmd). */
    MERMAID("mermaid");

    private final String id;

    DocFormat(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** "markdown,mermaid" -> those formats; blank -> all of them. */
    public static Set<DocFormat> parse(String list) {
        if (list == null || list.isBlank()) {
            return EnumSet.allOf(DocFormat.class);
        }
        Set<DocFormat> formats = EnumSet.noneOf(DocFormat.class);
        for (String name : list.split(",")) {
            String id = name.strip().toLowerCase(Locale.ROOT);
            formats.add(Arrays.stream(values()).filter(f -> f.id.equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown autodoc.docs.formats entry \"%s\"; valid: %s"
                            .formatted(name.strip(), Arrays.stream(values()).map(DocFormat::id)
                                    .collect(Collectors.joining(", "))))));
        }
        return formats;
    }
}
