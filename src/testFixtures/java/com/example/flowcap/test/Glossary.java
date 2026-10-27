package com.example.flowcap.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

/**
 * Business labels for endpoints ("POST /partner-b/matches") and fields (JsonPath expressions as written in
 * the expectations). Anything without a label gets a fallback, so the file is optional.
 */
public record Glossary(Map<String, String> endpoints, Map<String, String> fields) {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern INDEX = Pattern.compile("^(.*)\\[(\\d+)]$");
    private static final Pattern SIMPLE_PATH = Pattern.compile("^\\$(\\.[A-Za-z_][\\w-]*|\\[\\d+])+$");

    public Glossary {
        endpoints = endpoints == null ? Map.of() : Map.copyOf(endpoints);
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    static Glossary load(Path file) throws IOException {
        return Files.exists(file) ? JSON.readValue(Files.readString(file), Glossary.class) : new Glossary(null, null);
    }

    /** The endpoint's label, or the endpoint itself in code formatting. */
    String endpoint(String endpoint) {
        String label = endpoints.get(endpoint);
        return label != null ? label : "`" + endpoint + "`";
    }

    /**
     * "$.personId" -> its label, or "Person id" from the last segment. Indexed paths read as
     * "$.results[0].partner" -> "Partner (Results #1)"; a label for "$.results[*].partner" is used when
     * present. Expressions that aren't plain paths (filters, functions) are shown as code.
     */
    String field(String path) {
        String label = fields.get(path);
        if (label != null) {
            return label;
        }
        if (!SIMPLE_PATH.matcher(path).matches()) {
            return "`" + path + "`";
        }
        Matcher indexed = INDEX.matcher(path);
        if (indexed.matches()) {
            return field(indexed.group(1)) + " #" + (Integer.parseInt(indexed.group(2)) + 1);
        }
        int lastIndex = path.lastIndexOf('[');
        int lastDot = path.lastIndexOf('.');
        if (lastIndex > 0 && lastIndex < lastDot) {
            String wildcard = path.replaceAll("\\[\\d+]", "[*]");
            String base = fields.getOrDefault(wildcard, tidy(path.substring(lastDot + 1)));
            Matcher parent = INDEX.matcher(path.substring(0, lastDot));
            if (parent.matches()) {
                return "%s (%s #%d)".formatted(base, field(parent.group(1)), Integer.parseInt(parent.group(2)) + 1);
            }
            return base;
        }
        return tidy(path.substring(lastDot + 1));
    }

    /** "documentNumber" -> "Document number". */
    static String tidy(String name) {
        String words = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').replace('-', ' ')
                .toLowerCase(Locale.ROOT).strip();
        return words.isEmpty() ? name : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
