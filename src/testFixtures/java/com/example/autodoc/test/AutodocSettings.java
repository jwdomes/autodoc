package com.example.autodoc.test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Path;

/**
 * Where an app's flow docs come from and go to. Paths are relative to the module directory, which is
 * Gradle's working directory for tests.
 *
 * @param basePackage      package scanned for steps and participants
 * @param entryParticipant participant that receives requests, used when a request is rejected before any step
 * @param docsDir          output directory for the generated docs
 * @param scenariosDir     directory of scenario JSON files; {@link #of} reads it from the autodoc.scenarios.dir
 *                         system property, which the app's build forwards from gradle.properties
 * @param glossaryFile     optional labels for endpoints and fields in the expectation text
 * @param upstreamLabel    label of the caller lifeline in scenario diagrams
 */
public record AutodocSettings(String basePackage, String entryParticipant, Path docsDir, Path scenariosDir,
                              Path glossaryFile, String upstreamLabel) {

    public static AutodocSettings of(String basePackage, String entryParticipant) {
        return new AutodocSettings(basePackage, entryParticipant, Path.of("docs"),
                Path.of(System.getProperty("autodoc.scenarios.dir", "src/test/resources/scenarios")),
                Path.of("src/test/resources/autodoc-glossary.json"),
                "Upstream");
    }

    JavaClasses importClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);
    }
}
