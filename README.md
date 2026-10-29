# autodoc

autodoc generates flow documentation for a Spring Boot app from its tests. You annotate the
methods that matter with `@FlowStep` and describe scenarios as JSON files. Each scenario's stubs
stand in for downstream systems in WireMock, and its expectations are written in WireMock's own
matching syntax.

The scenario tests run each request through the app and check its expectations. They then write
these docs:

- **A sequence diagram per scenario**, as Mermaid and as an SVG image.
- **A Markdown page per scenario.** It shows the expected behavior in plain sentences, plus the
  request, the diagram, every downstream call and the response.
- **A static graph and index** of every step.

The app's functional code doesn't change: it only gains annotations. Unless switched on, autodoc
registers nothing at runtime.

**Requirements:** Spring Boot 4.x with Spring MVC, Java 21+, and Gradle (Groovy DSL).

---

# Part 1: This repo

## Layout

```
settings.gradle, build.gradle, gradle.properties     coordinates: com.example:autodoc:0.1.0
src/main/java/com/example/autodoc/                   annotations: @FlowStep, @FlowParticipant
src/main/java/com/example/autodoc/boot/              runtime: auto-configuration, interceptor
src/main/resources/META-INF/spring/...AutoConfiguration.imports
src/testFixtures/java/com/example/autodoc/test/      test support: runner, renderers, rules
src/test/java/com/example/autodoc/                   the library's own tests
templates/gatling/                                   load-test files apps copy (section 2.10)
scripts/change-group.sh                              swaps the com.example group and package
docs/                                                design notes
```

Apps get two jars:

| Jar | Source | Lands on the app's |
| --- | --- | --- |
| `autodoc-0.1.0.jar` | `src/main` | main classpath. It does nothing unless enabled. |
| `autodoc-0.1.0-test-fixtures.jar` | `src/testFixtures` | test classpath only, so ArchUnit, WireMock and JUnit never reach production |

## Build, test and publish

```bash
./gradlew build                  # compiles and runs the library's tests
./gradlew publishToMavenLocal    # writes com/example/autodoc/0.1.0/ to ~/.m2/repository
```

Publishing writes five files: the main jar, the test-fixtures jar, the sources jar, the `.pom`, and
the `.module` Gradle metadata. The `.module` file is what lets apps write
`testFixtures('com.example:autodoc:0.1.0')`. Only Gradle reads it.

`publishToMavenLocal` only reaches builds on the same machine. To share autodoc with a team,
publish it to your organization's Maven repository the way you publish your other internal
libraries, usually from CI. All five files must be published, including the `.module` file.

**Versioning.** autodoc uses semantic versioning. Count any of these as a major change:

- an annotation change that breaks app code
- an expectation syntax change
- a change that makes docs render differently, because apps that verify their docs must regenerate

**The Boot BOM** in `build.gradle` names the oldest Spring Boot version autodoc supports. Apps on a
newer Boot keep their own versions.

## Changing the group and package

The default group and package root is `com.example`. To use your own, for example `org.myco`, run
`scripts/change-group.sh` from a terminal in the repo root (bash, on Linux, macOS or Git Bash on
Windows), then rebuild:

```bash
cd autodoc                          # your clone of this repo
scripts/change-group.sh org.myco    # your organization's group
./gradlew clean build publishToMavenLocal
git add -A && git commit -m "Change group to org.myco"
```

The script reads the current group from `gradle.properties`, so it also works for a later change,
say from `org.myco` to `org.myco.platform`. It refuses a malformed group or the current one. It
doesn't commit, so check `git status` before committing.

The script does three things:

- It changes `group` in `gradle.properties`.
- It moves the source folders.
- It rewrites every `package` and `import` line, including the one line in
  `src/main/resources/META-INF/spring/...AutoConfiguration.imports`. If that line is missed,
  autodoc silently never switches on.

Apps then depend on `org.myco:autodoc:<version>` and import `org.myco.autodoc...` in place of
`com.example...`.

## Working on autodoc against a real app

To try an unreleased change, add `includeBuild '../autodoc'` to the app repo's `settings.gradle`.
Remove it before committing. Gradle then builds autodoc from source in place of the published jar,
test fixtures included.

---

# Part 2: Adding autodoc to an app

Placeholders: `<app>` is the app's Gradle module directory (the repo root for a single-project
build). `com.acme.app` is the app's base package.

## 2.1 Repositories

The app's build must be able to read wherever autodoc is published:

- **Locally:** `mavenLocal()`.
- **Otherwise:** your shared repository.

Add it where the repo already declares repositories:

- **In `settings.gradle`:** inside `dependencyResolutionManagement { repositories { ... } }`.
- **In the root `build.gradle`:** inside `subprojects { repositories { ... } }`. A bare top-level
  `repositories {}` block applies to the root project only.

## 2.2 App `build.gradle`

```groovy
dependencies {
    implementation 'com.example:autodoc:0.1.0'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'     // provides ObservationRegistry

    testImplementation testFixtures('com.example:autodoc:0.1.0')
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-webmvc-test'     // @AutoConfigureMockMvc
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
    // -Pautodoc.docs.formats=... and -Pautodoc.docs.verify=true reach autodoc as system properties
    ['autodoc.docs.formats', 'autodoc.docs.verify'].each { key ->
        def value = providers.gradleProperty(key)
        if (value.present) {
            systemProperty key, value.get()
        }
    }
    // The docs are inputs when they're verified, and outputs when the tests write them.
    if (providers.gradleProperty('autodoc.docs.verify').getOrElse('false') == 'true') {
        inputs.dir('docs').optional()
    } else {
        outputs.dir('docs')
    }
}
```

Notes:

- **Actuator.** With autodoc switched on, the app needs an `ObservationRegistry` bean. Actuator
  provides one, and so does `spring-boot-micrometer-observation` on its own. Startup fails loudly
  without it.
- **`-parameters`.** The Spring Boot Gradle plugin compiles with it, so notes can refer to
  arguments as `#request`. Without the plugin, add
  `tasks.withType(JavaCompile).configureEach { options.compilerArgs.add('-parameters') }`.
- **Running one test class:** `./gradlew :<app>:test --tests '...'`. The root project is `:test`.

**Repo root `.gitattributes`:**

```
*.mmd text eol=lf
*.svg text eol=lf
```

## 2.3 Switching it on

| Property | Default | Where |
| --- | --- | --- |
| `autodoc.observations.enabled` | off | On in the scenario tests' `@SpringBootTest(properties = ...)`, and on the command line for a load test. Never set in production. |

When it's off, autodoc registers nothing: no beans, no proxies, no expression parsing. When it's
on, every `@FlowStep` call is recorded as a `autodoc.step` observation, tagged with `autodoc.step`
and `autodoc.participant`.

## 2.4 Participants and step names

`<app>/src/main/java/com/acme/app/flow/Participants.java`. Participants are the systems in the
diagrams, declared as constants:

```java
public final class Participants {

    @FlowParticipant(label = "Orders Service")
    public static final String ORDERS = "ORDERS";

    @FlowParticipant(label = "Inventory")
    public static final String INVENTORY = "INVENTORY";

    private Participants() {
    }
}
```

`<app>/src/main/java/com/acme/app/flow/Steps.java`. Step actions are each step's identity, so keep
them as constants and a rename happens in one place:

```java
public final class Steps {

    public static final String RECEIVE_ORDER = "Receive order";
    public static final String CHECK_INVENTORY = "Check inventory";
    public static final String MAP_INVENTORY_FAILURE_TO_502 = "Map inventory failure to 502";

    private Steps() {
    }
}
```

Both can live anywhere under the base package.

## 2.5 `@FlowStep`

Put it on the controller method, on orchestration methods, on each method that calls a downstream
system, and on `@ExceptionHandler` methods. Method bodies don't change.

```java
@FlowStep(action = Steps.RECEIVE_ORDER, participant = Participants.ORDERS)
@PostMapping("/orders")
public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest request) { ... }

@FlowStep(action = Steps.CHECK_INVENTORY, participant = Participants.INVENTORY,
          note = "SKU #{#request.sku()}")          // optional SpEL template, shown before the call
public Stock checkInventory(OrderRequest request) {
    return inventoryClient.check(request.sku());
}

@FlowStep(action = Steps.MAP_INVENTORY_FAILURE_TO_502, participant = Participants.ORDERS)
@ExceptionHandler(InventoryException.class)
public ProblemDetail inventoryFailed(InventoryException e) { ... }
```

- **`participant`** is the system the step happens in. For a downstream call, that's the
  downstream system, so the diagram draws an arrow to it.
- **Notes** can read arguments (`#name`), `#hasText(...)` and `#property('key')`. A note that fails
  to evaluate never affects the call, but it fails the scenario that hits it.
- **Decisions aren't annotated.** Which systems are called, and why, is stated by each scenario's
  expectations (section 2.8).
- **Client libraries you don't own.** Annotate the app method that calls the client, and add the
  `downstreamCallsAreSteps` rule (section 2.7).

**Rules**, enforced by `AutodocRules`:

- Steps are public or package-private, not `private`, `final` or `static`. They must never be
  called from their own class, because Spring's proxy only sees calls from other beans.
- Step actions are unique.
- Every participant is declared.
- Every note parses.
- Methods calling `RestClient`, `RestTemplate` or `WebClient` are steps.

## 2.6 Test settings

Put all the test classes in one package, for example `<app>/src/test/java/com/acme/app/docs/`.

`AppAutodoc.java`:

```java
final class AppAutodoc {

    // Docs go to <app>/docs/; scenarios come from <app>/src/test/resources/flow-scenarios/;
    // the glossary is <app>/src/test/resources/autodoc-glossary.json
    static final AutodocSettings SETTINGS = AutodocSettings.of("com.acme.app", Participants.ORDERS);

    private AppAutodoc() {
    }
}
```

The second argument is the participant that receives incoming requests.

## 2.7 Test classes

**`FlowDocsTest.java`**: the static graph and index.

```java
class FlowDocsTest {

    @Test
    void flowDocsAreCurrent() throws Exception {
        StaticAutodoc.verify(AppAutodoc.SETTINGS);
    }
}
```

**`AutodocRulesTest.java`**:

```java
@AnalyzeClasses(packages = "com.acme.app", importOptions = ImportOption.DoNotIncludeTests.class)
class AutodocRulesTest {

    @ArchTest
    static final ArchTests autodoc = ArchTests.in(AutodocRules.class);

    // Only if downstream calls go through client interfaces from another library
    @ArchTest
    static final ArchRule clientCallsAreSteps = AutodocRules.downstreamCallsAreSteps("com.acme.clients..");
}
```

**`ScenarioDocsTestSupport.java`**: shared wiring for the scenario tests.

```java
@AutoConfigureMockMvc
@Import(FlowRecorderConfiguration.class)
abstract class ScenarioDocsTestSupport {

    // Plain-text HTTP/2 off: WireMock's Jetty resets the JDK client's h2c upgrade attempt.
    @RegisterExtension
    static WireMockExtension downstream = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
            .build();

    private static final ScenarioRunner RUNNER = new ScenarioRunner(AppAutodoc.SETTINGS, downstream);

    // Every downstream base URL points at the one WireMock, each under its own path
    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        registry.add("inventory.base-url", () -> downstream.baseUrl() + "/inventory");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    FlowRecorder recorder;

    void run(String scenario) throws Exception {
        RUNNER.run(scenario, mvc, recorder);
    }
}
```

**`FlowScenarioDocsTest.java`**: the scenarios for the default configuration.

```java
@SpringBootTest(properties = "autodoc.observations.enabled=true")
class FlowScenarioDocsTest extends ScenarioDocsTestSupport {

    static Stream<String> scenarios() throws IOException {
        return Scenario.names(AppAutodoc.SETTINGS.scenariosDir(), Map.of());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void scenario(String name) throws Exception {
        run(name);
    }
}
```

**One more class per alternative configuration.** A Spring context has one fixed configuration.
So scenarios that need, say, `pricing.use-new=true` declare it in their `properties`, and run from
a class with that setting:

```java
@SpringBootTest(properties = {"autodoc.observations.enabled=true", "pricing.use-new=true"})
class NewPricingScenarioDocsTest extends ScenarioDocsTestSupport {

    static Stream<String> scenarios() throws IOException {
        return Scenario.names(AppAutodoc.SETTINGS.scenariosDir(), Map.of("pricing.use-new", "true"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void scenario(String name) throws Exception {
        run(name);
    }
}
```

**`FlowObservationsOffTest.java`** (optional) proves autodoc is inert by default:

```java
@SpringBootTest
class FlowObservationsOffTest {

    @Autowired
    InventoryService inventoryService;      // any bean with a @FlowStep method

    @Test
    void stepsAreNotProxiedByDefault() {
        assertThat(AopUtils.isAopProxy(inventoryService)).isFalse();
    }
}
```

## 2.8 Scenarios and expectations

Each scenario is one JSON file at `<app>/src/test/resources/flow-scenarios/<name>.json`. The file
name is the scenario's name.

```json
{
  "description": "Physical item in stock: inventory is checked",
  "method": "POST",
  "path": "/orders",
  "request": { "sku": "SKU-1001", "type": "PHYSICAL" },
  "expectedStatus": 200,
  "expect": {
    "calls": [
      { "request": { "method": "GET", "urlPath": "/inventory/stock/SKU-1001" }, "count": 1,
        "why": "Physical items need a stock check" },
      { "request": { "method": "POST", "urlPath": "/pricing/quote",
          "bodyPatterns": [ { "matchesJsonPath": { "expression": "$.sku", "equalTo": "SKU-1001" } },
                            { "matchesJsonPath": { "expression": "$.discountCode", "absent": true } } ] } },
      { "request": { "method": "POST", "urlPath": "/backorder" }, "count": 0,
        "why": "The item is in stock" }
    ],
    "response": { "bodyPatterns": [
      { "matchesJsonPath": { "expression": "$.status", "equalTo": "PLACED" } },
      { "matchesJsonPath": { "expression": "$.lines.length()", "equalTo": "1" } } ] }
  },
  "stubs": [
    { "request": { "method": "GET", "urlPath": "/inventory/stock/SKU-1001" },
      "response": { "status": 200, "headers": { "Content-Type": "application/json" },
                    "jsonBody": { "available": 12 } } }
  ]
}
```

| Field | Notes |
| --- | --- |
| `description` | One line, shown on the page and in the diagram |
| `method`, `path`, `request` | The request sent to the app |
| `properties` | Optional, for example `{ "pricing.use-new": "true" }`. Only the test class with exactly these properties runs the scenario. |
| `expectedStatus` | The status the app must return |
| `expect` | Optional expectations, below |
| `stubs` | Standard WireMock mappings. Paths start with the prefixes set in `downstreamUrls`. |

**Expectations** use WireMock's own syntax:

- **`expect.calls`.** Each entry is a WireMock request pattern, the same `request` block a stub
  uses, plus:
  - `count`: the exact number of matching calls. Leave it out for "at least once".
  - `why`: optional text explaining it.
- **`count: 0` applies to the whole pattern.** With body patterns, it means "not called with these
  values". For "not called at all", give only the method and URL.
- **`expect.response.bodyPatterns`** checks the app's response body with WireMock matchers.
  `expectedStatus` covers the status.

All expectations are checked after each run. Every failure is reported together, and a call
pattern that matched nothing shows WireMock's diff against the closest request.

**Readable text.** The page's "Expected behavior" section turns these patterns into sentences:

| Pattern | Reads as |
| --- | --- |
| method and `urlPath` / `url` / `urlPathPattern` | the endpoint's label |
| `count` absent / 0 / 1 / n | is called / is not called / is called once / is called n times |
| `matchesJsonPath: "$.x"` | X is provided |
| `matchesJsonPath: {expression: "$.x", equalTo: v}` | X is v |
| `matchesJsonPath: {expression: "$.x", absent: true}` | X is not provided |
| `matchesJsonPath: {expression: "$.x.length()", equalTo: n}` | X has n entries |
| a header or query parameter with `equalTo` | Header H is v |

Any other pattern is shown as-is in code formatting.

**Glossary** (optional), at `<app>/src/test/resources/autodoc-glossary.json`, holds business labels:

```json
{
  "endpoints": { "GET /inventory/stock/SKU-1001": "Inventory stock check" },
  "fields": { "$.sku": "SKU", "$.lines": "Order lines", "$.lines[*].price": "Line price" }
}
```

- **Endpoint keys** are `"<METHOD> <url>"`.
- **Field keys** are the JsonPath expressions as written in the patterns. A `[*]` key labels every
  index.
- **Fallbacks.** An unlabeled field becomes its tidied name, so `$.documentNumber` reads
  "Document number" and `$.lines[0].price` reads "Price (Lines #1)". An unlabeled endpoint is shown
  as code.

**Stub rules:**

- **Unique keys.** Match each stub on a value unique to its scenario. Gatling loads every scenario's
  stubs at once.
- **Content type.** Stub responses need `Content-Type: application/json`.
- **No random delays.** The docs must come out the same on every run.

## 2.9 Generated docs

By default, the tests write every format on every run. Two properties change that, set with
`-P` on the Gradle command line or in `gradle.properties`:

| Property | Default | Effect |
| --- | --- | --- |
| `autodoc.docs.formats` | `markdown,markdown-image,mermaid` | Which formats to write |
| `autodoc.docs.verify` | `false` | `true`: write nothing, and fail if any committed doc differs from what would be written. It also fails if a scenario has no page, meaning no test class runs its properties. Use it in CI. |

Output under `<app>/docs/`:

| Format | Static | Per scenario |
| --- | --- | --- |
| `mermaid` | `flow.mmd` | `flows/<name>.mmd` |
| `markdown` | `README.md`, with the Mermaid graph | `flows/<name>.md`, with the Mermaid diagram |
| `markdown-image` | `markdown-image/README.md` + `flow.svg` | `markdown-image/flows/<name>.md` + `<name>.svg` |

`markdown-image/` is self-contained, for tools that can't render Mermaid, such as Confluence without
a plugin. Its pages show the diagrams as SVG images next to them, and link to each other by relative
paths. The SVGs use monospace text, so they're byte-identical on every machine.

Typical flow:

```bash
./gradlew build                                   # writes the docs
git add <app>/docs && git commit
./gradlew build -Pautodoc.docs.verify=true        # in CI: fails if anyone forgot to commit docs
```

## 2.10 Optional: Gatling load test

Copy these files:

- `templates/gatling/FlowScenariosSimulation.java` to `<app>/src/gatling/java/<package>/`, then fix
  its `package` line.
- `templates/gatling/logback.xml` to `<app>/src/gatling/resources/`.

Then:

1. Add `id 'io.gatling.gradle' version '3.16.0'` to the app's plugins.
2. Create `<app>/src/main/resources/application-wiremock.yml`, pointing every downstream base URL
   at WireMock with the same path prefixes as `downstreamUrls`.
3. Run WireMock with `--disable-http2-plain`.
4. Start the app with
   `java -jar <app>.jar --spring.profiles.active=wiremock --autodoc.observations.enabled=true`.
5. Run `./gradlew :<app>:gatlingRun`. These environment variables are optional:
   - `APP_URL`, `WIREMOCK_URL`
   - `PARTNER_DELAY_MS`
   - `PEAK_USERS_PER_SEC`, `RAMP_SECONDS`
   - `GATLING_PROPERTIES`: a property set such as `pricing.use-new=true`. Start the app with the same
     settings.
6. Read per-step timings from `/actuator/metrics/autodoc.step`. Expose `metrics` with
   `management.endpoints.web.exposure.include=health,metrics`.

The simulation ignores `expect`.

## 2.11 Checklist: files the app gains

```
.gitattributes                                    *.mmd and *.svg eol=lf
<app>/build.gradle                                dependencies and test task (2.2)
<app>/src/main/java/com/acme/app/flow/Participants.java, Steps.java
<app>/src/main/java/...                           @FlowStep on existing methods
<app>/src/test/java/com/acme/app/docs/            AppAutodoc, FlowDocsTest, AutodocRulesTest,
                                                  ScenarioDocsTestSupport, FlowScenarioDocsTest,
                                                  one subclass per property set, FlowObservationsOffTest
<app>/src/test/resources/flow-scenarios/*.json
<app>/src/test/resources/autodoc-glossary.json    optional
<app>/docs/                                       generated, committed
```

## Upgrading autodoc in an app

1. Bump the version.
2. Run `./gradlew build` and review the doc diff.
3. Commit the bump and the docs together.

## Limits

- **Spring MVC only.** WebFlux would need context propagation to keep steps nested across threads.
- **Proxies.** Calls within one class, and `private`, `final` or `static` methods, aren't recorded.
  `docs/load-time-weaving.md` proposes a fix.
- **Parallel calls.** Steps that overlap in time are drawn inside a `par` block.
- **400 bodies.** MockMvc doesn't render the servlet container's error page, so a request rejected
  before the controller shows an empty body.
