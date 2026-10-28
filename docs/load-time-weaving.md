# Load-Time Weaving Plan

Oct 27, 2026 · Plan gist only, nothing built yet

## Problem

autodoc intercepts annotated methods through Spring AOP proxies. A proxy only sees calls that come
in through it, so these are never recorded:

- a call from one method to another in the same class (it goes through `this`)
- `private`, `final` and `static` methods

Today the `noSelfInvocation` and `flowMethodsAreProxyable` rules block these at build time. The only
fix is moving code into another bean, which breaks the add-only rule.

## Approach

Add AspectJ load-time weaving as a second interception mode, alongside the proxies.

- A Java agent (`-javaagent:aspectjweaver.jar`) rewrites annotated methods' bytecode as their class
  loads. The interception lives inside the method itself, so every call is caught.
- The agent is started only in the test JVM, and in the app JVM for a Gatling run. Production never
  gets it, so nothing is woven there.
- Annotations don't change.

## Library changes (this repo)

1. **Aspect.** A `FlowStepAspect` around `@FlowStep` executions that delegates to
   the existing `FlowStepInterceptor` logic.
2. **Library `META-INF/aop.xml`.** Declares the aspect and includes `com.example.autodoc..*`. It's
   fixed and the same for every app. AspectJ merges every `aop.xml` on the classpath, so apps add only
   their own package.
3. **Auto-configuration picks one mode.**
   - If the agent is present: wire the aspect and register no proxy advisor. Both modes at once
     would record every step twice.
   - If the agent is absent: use proxies as today.
   - Either way, nothing happens unless `autodoc.observations.enabled=true`.
4. **Fail loudly on a silent gap.** If observations are on, the agent is absent, and an annotated
   method can't be proxied (private, final, static), fail at startup. Otherwise those steps would
   quietly vanish from the docs.
5. **Rules.** `noSelfInvocation` and `flowMethodsAreProxyable` stay available but become optional.
   A weaving-mode app drops them from its `AutodocRulesTest`.
6. **Tests.**
   - a self-call fixture and a private-method fixture, run with the agent
   - the startup failure without it
   - no double recording when both modes could apply

## What a target project sees

| What | Change |
| --- | --- |
| Annotations and functional code | None |
| `src/test/resources/META-INF/aop.xml` | New: one `include` line for the app's package |
| App `build.gradle` | Agent wiring on the test task (below) |
| `AutodocRulesTest` | Drop `noSelfInvocation` and `flowMethodsAreProxyable` |
| Generated docs | Self-calls now appear; regenerate once with `a test run` |
| Gatling app run | Add `-javaagent:...aspectjweaver.jar` to `java -jar` |
| Production | Nothing |

App `aop.xml`:

```xml
<aspectj>
    <weaver>
        <include within="com.mycompany.myapp..*"/>
    </weaver>
</aspectj>
```

The package is the same one passed to `AutodocSettings.of(basePackage, ...)`. It changes only on a
top-level package rename.

App `build.gradle`:

```groovy
configurations {
    aspectjAgent
}

dependencies {
    aspectjAgent 'org.aspectj:aspectjweaver:<version>'
}

tasks.named('test') {
    jvmArgumentProviders.add({ ["-javaagent:${configurations.aspectjAgent.singleFile}"] } as CommandLineArgumentProvider)
}
```

## Private methods

With the agent, `private`, `final` and `static` methods can be steps. The static graph
already handles them, because ArchUnit sees private calls.

- This commits the project to weaving. Going back to proxies means making those methods
  non-private again.
- Keep steps at boundaries a reader cares about: downstream calls and orchestration. A private
  helper annotated for its own sake ties the docs to internal structure.

## Costs and risks

- **Dependency.** `aspectjweaver` (about 2 MB), for tests and the Gatling run only.
- **Gradle wiring.** A few lines per app, written once.
- **Load order.** A class loaded before the agent starts isn't woven. That's rare for app code,
  because the agent starts before `main`.
- **Debugging.** Stack traces gain woven frames.
- **Misconfiguration.** A wrong package in `aop.xml` means nothing is woven. Snapshots would then
  fail with near-empty diagrams. A dedicated check could say why directly.

## Open questions

- **Several Spring contexts in one test JVM.** The aspect is a singleton created by AspectJ, not
  Spring. If it holds one static reference to an interceptor, a cached context from an earlier test
  class could record into a later context's registry. Possible fix: register each woven bean with
  its own context's interceptor (a `BeanPostProcessor`), and look up the interceptor by the target
  object on each call. Static methods have no target object, so they need a separate rule.
- **Where the aspect class lives.**
  - **`src/main`:** the Gatling app run (the real jar) can use it. It's inert without the agent, and
    `aspectjweaver` is `compileOnly`.
  - **Test fixtures:** it stays off the main classpath, but the Gatling run then can't weave.
- **Gatling's `aop.xml`.** The app's `include` line lives in test resources, so it isn't in the jar.
  Pass it to the Gatling run with `-Dorg.aspectj.weaver.loadtime.configuration=file:...`, or move it
  into main resources, where it's inert without the agent.
- **Opt-in or default.** Should weaving be the recommended mode for new adopters, with proxies as
  the fallback, or the other way round?

## Verification

- `./gradlew build` passes with the agent on, using the existing scenarios.
- A new scenario with a same-class call and a private step shows both in its diagram.
- Without the agent, a private step fails startup with a clear message.
- The prod-neutral checks still pass: no beans, proxies or weaving when off.
- Gatling, with the agent, reports `autodoc.step` metrics for the self-called step.
