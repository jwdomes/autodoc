package com.example.autodoc.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Writes the generated docs, or with verify on, compares each with its committed copy and fails when one is
 * stale. Configured by the system properties autodoc.docs.formats (default: all) and autodoc.docs.verify
 * (default: false).
 */
public final class DocWriter {

    private final Set<DocFormat> formats;
    private final boolean verify;

    public DocWriter(Set<DocFormat> formats, boolean verify) {
        this.formats = Set.copyOf(formats);
        this.verify = verify;
    }

    public static DocWriter fromSystemProperties() {
        return new DocWriter(DocFormat.parse(System.getProperty("autodoc.docs.formats")),
                Boolean.getBoolean("autodoc.docs.verify"));
    }

    public boolean writes(DocFormat format) {
        return formats.contains(format);
    }

    boolean verifying() {
        return verify;
    }

    public void write(Path path, String content) throws IOException {
        if (verify) {
            String committed = Files.exists(path) ? Files.readString(path).replace("\r\n", "\n") : "";
            assertEquals(committed, content, "Flow docs out of date: " + path
                    + ". Run the tests without -Pautodoc.docs.verify=true and commit the docs");
            return;
        }
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, content);
    }
}
