package com.example.autodoc.test;

import java.util.Locale;

final class FlowIds {

    /** "Call Partner A" -> "call_partner_a": Mermaid node ids, and the uniqueness rule's key. */
    static String slug(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    /** Quotes in Mermaid labels. */
    static String escape(String text) {
        return text.replace("\"", "#quot;");
    }

    private FlowIds() {
    }
}
