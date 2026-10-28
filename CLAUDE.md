# autodoc

A library that generates flow docs for Spring Boot 4 apps: `@FlowStep` annotations, plus JSON
scenarios whose stubs and expectations use WireMock's own syntax. `README.md` is the full guide.
The reference app is the sibling repo `autodoc-implementation`.

## Layout

```
src/main/java/com/example/autodoc/        @FlowStep, @FlowParticipant
src/main/java/com/example/autodoc/boot/   AutodocAutoConfiguration, FlowStepInterceptor, FlowExpressions
src/testFixtures/java/.../autodoc/test/   what apps use in tests (published as the test-fixtures jar)
src/test/java/...                         the library's own tests; fixture/ and clientcall/ are sample apps
templates/gatling/                        load-test files apps copy
docs/                                     design notes
```

## Build

```bash
./gradlew build                  # library tests
./gradlew publishToMavenLocal    # com.example:autodoc:<version> into ~/.m2, which autodoc-implementation reads
```

After a change, republish, then run autodoc-implementation's build to see the effect on real docs.

## Rules

- **Add-only.** Apps gain annotations and test code. Their functional code never changes for autodoc.
- **Performance-neutral in production.** Unless `autodoc.observations.enabled=true`,
  `AutodocAutoConfiguration` registers nothing. `AutodocAutoConfigurationTest` checks this.
- **Deterministic output.** Every generated doc must be byte-identical across runs and machines.
  That means no timestamps, sorted output, and monospace SVG text sized by character count
  (`SvgText`). Otherwise `autodoc.docs.verify=true` breaks.
- **One model, two renderers.** Sequence diagrams are built once as a `SequenceModel` and drawn by
  `MermaidSequence` and `SvgSequence`. The static graph comes from `FlowGraph`, drawn by
  `FlowGraph.mermaid()` and `SvgGraph`.
- **Expectations are WireMock syntax.** `Expectations` parses them with WireMock's own JSON reader
  and checks them with WireMock's matchers. `ExpectationText` renders the common matchers as
  sentences, and shows anything else as code.
