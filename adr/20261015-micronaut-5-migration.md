# Micronaut 4 → 5 migration record

Record of every change needed to move Wave from Micronaut 4.10.21 to Micronaut 5.2.2
(core 5.2.15), written so the same recipe can be replayed on other Seqera Micronaut/Groovy
services. Entries are grouped by the reason that forced them; each one says *what* broke,
*why*, and the fix applied.

Versions that come together with the Micronaut 5.2.2 platform BOM and matter for us:

| Component | Micronaut 4.10.x | Micronaut 5.2.2 |
|---|---|---|
| Java baseline | 17 (Wave already on 25) | **25** (hard requirement, also for the Gradle daemon) |
| Micronaut Gradle plugin | 4.1.1 | **5.0.2** |
| Groovy | 4.0.x | **5.0.8** |
| Spock | 2.3-groovy-4.0 | **2.5-groovy-5.0** (BOM pins 2.4; 2.5 is the current Groovy 5 build) |
| Jackson | 2.x (`com.fasterxml.jackson.*`) | **3.2.3 (`tools.jackson.*`)**, annotations stay `com.fasterxml.jackson.annotation` 2.22 |
| Netty | 4.2.19 | 4.2.19 |
| Micronaut Security | 4.x | 5.4.0 |
| Micronaut Data / SQL | 4.x | 5.2.2 / 7.2.0 |
| Micronaut Micrometer / Views / Cache / Object Storage / AWS | 5.x | 6.1.0 / 6.3.1 / 6.1.1 / 3.1.0 / 5.1.1 |
| Testcontainers | 2.0.5 | 2.0.5 (new artifact ids already in use) |

Upstream references: [Micronaut 5 upgrade wiki](https://github.com/micronaut-projects/micronaut-core/wiki/Update-to-Micronaut-5),
[Micronaut 5.0.0 release notes](https://micronaut.io/2026/05/20/micronaut-framework-5-0-0-released/),
[Micronaut 5 with Jackson 3](https://micronaut.io/2026/05/01/micronaut-framework-5-with-jackson-3/),
[OpenRewrite `Micronaut4to5Migration`](https://docs.openrewrite.org/recipes/java/micronaut/micronaut4to5migration).

## 1. Build

### 1.1 Version bumps

- `gradle.properties`: `micronautVersion=5.2.2`, `nettyVersion=4.2.19.Final` (aligned with the platform BOM).
- `build.gradle`: `id "io.micronaut.minimal.application" version "5.0.2"`.
- `buildSrc/.../io.seqera.wave.java-library-conventions.gradle`: test dependencies moved to
  `org.apache.groovy:groovy*:5.0.8` and `org.spockframework:spock-*:2.5-groovy-5.0`. The platform
  BOM pins Groovy 5 for the application, so keeping Groovy 4 / Spock 2.3 in the shared conventions
  would have mixed Groovy runtimes between the app and the `wave-api`/`wave-utils` subprojects.
- Gradle wrapper 9.6.1 was already newer than the 9.5 minimum of the plugin; no change.

### 1.2 Gradle daemon must run on Java 25 (gotcha)

`io.micronaut.gradle:micronaut-minimal-plugin:5.0.2` is published with a Java 25 Gradle module
attribute, so **the Gradle daemon JVM itself** must be Java 25 — a Java 25 *toolchain* is not
enough. Symptom:

```
Could not resolve io.micronaut.gradle:micronaut-minimal-plugin:5.0.2.
  > Dependency requires at least JVM runtime version 25. This build uses a Java 21 JVM.
```

Fix: commit `gradle/gradle-daemon-jvm.properties` with `toolchainVersion=25` (Gradle's *daemon
JVM criteria*). Gradle then picks a matching JDK from auto-detected installs (SDKMAN!, `JAVA_HOME`,
…) regardless of the JVM used to launch `./gradlew`. `./gradlew updateDaemonJvm --jvm-version=25`
cannot generate it after the bump because configuration already fails, so write the file by hand
(or run the task *before* bumping the plugin). CI (`setup-java` 25) is unaffected.

### 1.3 Jackson 3 dependencies

- `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` → `tools.jackson.dataformat:jackson-dataformat-yaml`
  (version managed by the Micronaut BOM).
- `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` removed: `java.time` support is built into
  Jackson 3 `jackson-databind` (the 3.x artifact only exists as `3.0.0-rc2`).
- `platform('com.fasterxml.jackson:jackson-bom:2.23.0')` **kept**. The Micronaut BOM manages
  `tools.jackson.*` 3.2.3 and `com.fasterxml.jackson.core:jackson-annotations`, but Jackson 2 stays
  on the runtime classpath transitively (`io.kubernetes:client-java-api`, see §5.1), and master
  pins this BOM to keep those transitive Jackson 2 artifacts on a patched version. Dropping it
  would silently roll Jackson 2 back to whatever the Kubernetes client requests.

Seqera libraries still declaring Jackson 2 *annotations* (`lib-pairing` → `jackson-annotations:2.17.2`)
are fine: the annotations artifact keeps the `com.fasterxml.jackson.annotation` package in Jackson 3.

### 1.4 Seqera `lib-*` dependencies compiled against Micronaut 4

`lib-mail`, `lib-pairing`, `lib-data-*-redis`, `lib-jedis-pool`, `lib-cache-tiered-redis`,
`lib-fixtures-redis` are published against `micronaut-inject` 4.8.18/4.10.30. No Micronaut 5 builds
exist on `maven.seqera.io`. Their precompiled bean definitions load unchanged on the Micronaut 5
runtime (verified by the test suite, see §5), so no republish was required for this migration.

## 2. Configuration

`micronaut-jackson-databind` 5 renamed the `JacksonConfiguration` feature maps and Jackson 3 moved
date features out of `SerializationFeature`:

| Micronaut 4 property | Micronaut 5 property |
|---|---|
| `jackson.serialization.*` (`SerializationFeature`) | `jackson.serialization-features.*` |
| `jackson.deserialization.*` | `jackson.deserialization-features.*` |
| `jackson.mapper.*` | `jackson.mapper-features.*` |
| `jackson.parser.*` / `jackson.generator.*` | `jackson.stream-read-features.*` / `jackson.stream-write-features.*` (+ `json-read-features`, `json-write-features`) |
| – | `jackson.date-time-features.*` (`DateTimeFeature`, e.g. `writeDatesAsTimestamps`) |
| – | `jackson.enum-features.*` (`EnumFeature`) |

Wave: `application.yml` `jackson.serialization.writeDatesAsTimestamps: false` →
`jackson.date-time-features.writeDatesAsTimestamps: false` (`WRITE_DATES_AS_TIMESTAMPS` is a
`DateTimeFeature` in Jackson 3 and already defaults to `false` there).

### 2.1 Jackson 3 changed several defaults — restore the Jackson 2 ones

Jackson 3 flips a number of `MapperFeature`/`DeserializationFeature` defaults. The ones that broke
Wave (JSON bodies deserialised with every field `null`, Spock mocks of `@Canonical` classes) and
the values restored, both in the Micronaut-managed mapper (`application.yml`, `jackson.*`) and in
the hand-built mappers (`JacksonHelper`):

| Feature | Jackson 2 | Jackson 3 | Restored | Why it matters for Groovy code |
|---|---|---|---|---|
| `MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS` | true | **false** | true | `@Canonical`/`final` fields with a no-arg constructor are no longer populated → every property `null` |
| `MapperFeature.USE_GETTERS_AS_SETTERS` | true | **false** | true | `final List<X> items = []` populated through the getter |
| `MapperFeature.DEFAULT_VIEW_INCLUSION` | true | **false** | true | properties without `@JsonView` dropped when serialising with a view |
| `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY` | false | **true** | false | keeps declaration-order JSON output (tests comparing JSON strings) |
| `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES` | false | **true** | false | `null` in JSON for `int`/`boolean` properties used to become `0`/`false` |
| `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` | true | false | – | Micronaut already disabled it; Wave sets it explicitly |

Other flipped defaults, not restored (reviewed, no impact): `FAIL_ON_TRAILING_TOKENS` (now true),
`FAIL_ON_EMPTY_BEANS` (now false), `FIX_FIELD_NAME_UPPER_CASE_PREFIX`, `ALLOW_VOID_VALUED_PROPERTIES`.

```yaml
jackson:
  date-time-features:
    writeDatesAsTimestamps: false
  mapper-features:
    allowFinalFieldsAsMutators: true
    useGettersAsSetters: true
    defaultViewInclusion: true
    sortPropertiesAlphabetically: false
  deserialization-features:
    failOnNullForPrimitives: false
```

### 2.2 Duplicate `application-<env>.yml` resources are rejected

Micronaut 5 fails the context with
`ConfigurationException: Duplicate configuration resource 'application-rate-limit.yml' found on the classpath`
when the same environment file exists in both `src/main/resources` and `src/test/resources`
(Micronaut 4 silently merged them, test overriding main). Fix: rename the test override to
`application-rate-limit-test.yml` and add the `rate-limit-test` environment *after* `rate-limit`
in the tests that relied on the merge (`ApplicationContext.run('test', 'rate-limit', 'rate-limit-test')`,
`@MicronautTest(environments = [...])`).

## 3. Source changes

### 3.1 Jackson 2 → Jackson 3 (`tools.jackson.*`)

Package rename: `com.fasterxml.jackson.{core,databind,dataformat}` → `tools.jackson.{core,databind,dataformat}`.
Annotations (`@JsonProperty`, `@JsonInclude`, `@JsonCreator`, `@JsonValue`) are unchanged, but
`@JsonDeserialize` lives in `tools.jackson.databind.annotation`.

API changes hit by Wave (`JacksonHelper`, `postgres/Mapper`, `FusionVersionStringDeserializer`):

| Jackson 2 | Jackson 3 |
|---|---|
| `new ObjectMapper()` + `.configure()/.registerModule()/.setSerializationInclusion()/.setAnnotationIntrospector()` (mutable) | `JsonMapper.builder()...build()` — mappers are immutable; all configuration goes on the `MapperBuilder` (`.configure()`, `.addModule()`, `.changeDefaultPropertyInclusion()`, `.annotationIntrospector()`). Existing mapper → `mapper.rebuild()` |
| `new ObjectMapper(new YAMLFactory())` | `YAMLMapper.builder()` |
| `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS`, `DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE` | `tools.jackson.databind.cfg.DateTimeFeature.*` |
| `DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL` | `tools.jackson.databind.cfg.EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL` |
| `setSerializationInclusion(NON_ABSENT)` | `changeDefaultPropertyInclusion(v -> JsonInclude.Value.construct(NON_ABSENT, NON_ABSENT))` |
| `registerModule(new JavaTimeModule())` | drop — built in |
| `new ToStringSerializer()` | `ToStringSerializer.instance` (constructor is protected) |
| `JsonDeserializer<T>` / `deserialize(...) throws IOException` | `ValueDeserializer<T>` / `throws JacksonException` (unchecked) |
| `p.getCodec().readTree(p)` | `ctxt.readTree(p)` |
| `JacksonAnnotationIntrospector.hasIgnoreMarker(AnnotatedMember)` | `hasIgnoreMarker(MapperConfig<?>, AnnotatedMember)` |
| `JsonProcessingException` / `JsonMappingException` | `JacksonException` / `DatabindException` |
| `FAIL_ON_UNKNOWN_PROPERTIES` default `true` | default `false` |

Groovy gotcha: selecting the builder inline (`yaml ? YAMLMapper.builder() : JsonMapper.builder()`,
or an `if/else` assigning the same variable) makes `@CompileStatic` infer the type of the first
branch (`YAMLMapper.Builder`) and emit a cast that fails at runtime with
`ClassCastException: JsonMapper$Builder cannot be cast to YAMLMapper$Builder`. Return the builder
from a separate method declared as `MapperBuilder` instead (`JacksonHelper.newBuilder`).

Error-message assertions in tests that look for the Jackson package (`ErrorHandlingTest`) now check
both `com.fasterxml.jackson` and `tools.jackson`.

### 3.2 Micronaut maps Jackson annotations to introspection metadata (Groovy AST)

With `micronaut-inject-groovy` 5, a **private** method annotated with `@JsonProperty` fails
compilation:

```
Element annotated with @Introspected.Property cannot be used as an introspected property:
the method is not accessible for visibility [DEFAULT]
```

Fix: make the member non-private (`CredentialsDescription.unpackRegistry`).

### 3.3 `javax.annotation.*` → `jakarta.annotation.*` / Micronaut annotations

The JSR-250 `javax.annotation.PostConstruct` only reached the classpath transitively and Micronaut 5
documents only the `jakarta.annotation` form. Replaced proactively in 9 files with
`jakarta.annotation.PostConstruct` (already a declared dependency); JSR-305
`javax.annotation.Nullable` (5 files) replaced with `io.micronaut.core.annotation.Nullable`, which is
what the rest of the code base uses. `io.micronaut.core.annotation.Nullable`/`NonNull` are **not**
deprecated in Micronaut 5 (JSpecify is the new recommendation, but optional); they were kept to avoid
touching ~50 files and because JSpecify annotations are `TYPE_USE`-only, which Groovy handles less
predictably on fields/parameters.

### 3.4 Groovy 5 language changes

- Enum constants followed by field declarations must end with `;` — a trailing `,` is now a parse
  error (`ScanType`): `Unexpected input: ',\n\n    final'`.
- Stricter static type checking under `@CompileStatic`:
  - `new HashMap<String, ?>()` can no longer be `put` into → use `HashMap<String, Object>`.
  - `Mono.create { sink -> ... }` returned from a `Publisher<HttpResponse>` method no longer infers
    the type parameter → `Mono.<HttpResponse>create { MonoSink<HttpResponse> sink -> ... }`
    (`ViewController`).

### 3.5 Groovy 5 `groovy-nio`: `java.nio.file.Path` truth is now `Files.exists()`

In Groovy 4, `if( path )` / `assert path` on a `Path` was effectively a null-check (any non-empty
path was true). With `groovy-nio` 5 on the classpath `Path.asBoolean()` is `Files.exists(path)`, so
every truth-check on a path that does not exist on disk silently turns false. Twelve sites in Wave
(`assert workspace`, `assert workDir`, `if( credsFile )`, `if( configFile && ... )`) became explicit
`!= null` checks. Grep recipe used to find them: declared `Path` identifiers combined with
`if(`, `assert`, `?:`, `!`, `&&`, `||` usages (see git history of this migration). Note `String`
truth is unchanged.

Source: `org.apache.groovy.nio.extensions.NioExtensions.asBoolean(Path)` (`@since 5.0.0`), which
aligns `Path` with the long-standing `File` truth (`file.exists()`). Its javadoc documents an
escape hatch: the system property `groovy.truth.file.exists.enabled=false` restores the
pre-Groovy-5 behaviour (any non-null `Path` is truthy). We deliberately did **not** use it: it is a
JVM-wide switch that must be repeated on every launch path (`src/main/jib/launch.sh`, `run.sh`,
`lite/docker-compose.yml`, the Gradle `test` JVM) and silently reverts wherever it is forgotten,
whereas explicit `!= null` states the intent in the code. Reviewers of other migrations should
decide per project; in either case audit `assert path` / `if( path )` sites, because in
production most paths exist when checked and the bug stays latent (in Wave only
`MirrorRequest.create()` asserted a workspace before creating it).

### 3.6 Spock + Groovy 5: stubbing a `void` method no longer works

Groovy 5 `DefaultTypeTransformation.castToType(null, void.class)` throws
`GroovyCastException: Cannot cast 'null' to class 'void'` (Groovy 4 returned `null`; the same now
applies to `null` → primitive). Spock's `ConstantResponseGenerator` coerces a *constant* stubbed
response to the method's return type, so `1 * obj.voidMethod(...) >> null` fails at the call site.

A closure response is **not** affected: `CodeResponseGenerator` does not coerce the closure result,
so `>> { }` works on a `void` method and still suppresses the real method on a `Spy`. Verified on
Groovy 5.0.8 / Spock 2.5-groovy-5.0 with a probe spec covering all five combinations:

| Form | Result |
|---|---|
| `Mock`, `1 * t.voidMethod(x) >> null` | fails (`GroovyCastException`) |
| `Mock`, `1 * t.voidMethod(x)` | passes |
| `Spy`, `1 * t.voidMethod(x) >> null` | fails (`GroovyCastException`) |
| `Spy`, `1 * t.voidMethod(x) >> { }` | passes, real method suppressed |
| `Spy`, `1 * t.voidMethod(x) >> { String a -> }` | passes, real method suppressed |

- On a `Mock`/`Stub`: drop the `>> null`, it was redundant (13 interactions in 6 specs).
- On a `Spy`, where `>> null` was suppressing the real method: replace it with `>> { }`
  (`CleanupServiceTest`, `ManifestAssemblerTest`, `ContainerScanServiceImplTest`, `JobHelperTest`).
  No-op subclasses of the spied class also work and are the only option when the response must
  vary per call, but they are not needed for plain suppression.

Watch for *silent* occurrences: when the call site sits inside a `try/catch`, the
`GroovyCastException` is swallowed and the spec still passes while the stubbed path never runs
(`ContainerScanServiceImpl` logged `Unable to run the container scan` and
`ContainerScanServiceImplTest` reported 27/27 green). Grep the test sources for `>> null` and
check each target method's declared return type rather than relying on the suite going red.

## 4. Things checked and found *not* to need changes

- `micronaut-security`: Wave uses `@Secured`, `SecurityRule`, `HttpRequestAuthenticationProvider`,
  `AuthorizationException` — unchanged in 5.4.0. Only `@RolesAllowed`/`@PermitAll` users must swap
  `micronaut-security-annotations` for `micronaut-security-processor`.
- `micronaut-data` 5: the embedded-field naming-strategy break does not apply (no `@Embedded`).
- Micrometer `WebMetricsPublisher`, `micronaut-views-handlebars`, `micronaut-object-storage-aws`,
  `micronaut-aws-parameter-store`, `micronaut-cache-caffeine`, `micronaut-reactor`: no API changes hit.
- RxJava 2 removal: not used. Bootstrap configuration (`bootstrap*.yml`) is deprecated in 5 but still
  works; migration to `micronaut.config.imports` is a follow-up.
- Netty: already on 4.2.x; `-Dio.netty.allocator.type=pooled` from `adr/mv-4.9-netty-memory.md` still applies.

## 5. Verification

Baseline (Micronaut 4.10.21, local machine without registry/AWS credentials):
1861 tests, 53 failures, 16 skipped — all failures environmental (missing `DOCKER_*`/`QUAY_*`/AWS
credentials, Docker Hub `429 Too Many Requests`, live container builds).

Micronaut 5.2.2 (same machine, same conditions): 1861 tests, 53 failures, 16 skipped — the failing
set is identical to the baseline, test by test, with the same failure reasons (one test flipped
between `401 Unauthorized` and `429 Too Many Requests` from Docker Hub). `./gradlew assemble`
builds `wave-<version>.jar`. The credential-dependent tests are covered by CI, which runs with the
registry/AWS secrets.

Intermediate state worth knowing when replaying: after the first green compile the suite showed
100 *new* failures, all explained by the four runtime behaviour changes documented in §2.1, §2.2,
§3.5 and §3.6 (none of them is a compile error, so they only surface in tests).

### 5.1 Dependency collision check

Method: resolved `runtimeClasspath`/`testRuntimeClasspath` artifact lists dumped via an init script,
every jar scanned for class names shipped by more than one jar, the Gradle tree checked for any
module resolved *below* a requested version, and the same report produced for the Micronaut 4
baseline commit for comparison.

- No version downgrades. Netty is uniformly 4.2.19 (+ tcnative 2.0.84), Groovy uniformly 5.0.8 (no
  Groovy 4 or `org.codehaus.groovy` jars left), every Micronaut module at its BOM version, AWS SDK
  uniformly 2.54.3.
- Jackson 2 (`com.fasterxml.jackson.core:*` 2.22.2) stays on the classpath next to Jackson 3: it is
  required by `io.kubernetes:client-java-api`. Different packages, no class overlap; the Jackson 2
  `jackson-datatype-*`/`jackson-dataformat-yaml` modules are gone. `snakeyaml` 2.6 (Micronaut YAML
  config) and `snakeyaml-engine` 3.0.1 (Jackson 3 YAML) likewise coexist in different packages.
- Duplicate classes on the runtime classpath, identical on Micronaut 4 and 5, i.e. pre-existing:
  `io.kubernetes:client-java-api` ↔ `client-java-api-fluent` (the fluent jar re-bundles the API,
  same version) and `javax.activation:activation:1.1` ↔ `javax.activation-api:1.2.0` (via
  `lib-mail` → `javax.mail`). Test classpath only: `hamcrest-core:1.3` ↔ `hamcrest:3.0` (JUnit 4
  via `spock-junit4`), also pre-existing.
- New transitive modules: `micronaut-aws-distributed-configuration` (dependency of
  `micronaut-aws-parameter-store` 5; inert with `micronaut.config-client.enabled: false`, used by
  the `ec2` bootstrap), `netty-codec-multipart-core` (Micronaut 5 multipart refactor),
  `checker-qual`. Dropped: `commons-logging`, `LatencyUtils`, AWS `apache-client` (replaced by
  `apache5-client`).

## 6. Replay checklist for another project

1. Build runs on JDK 25: toolchain **and** daemon (`gradle/gradle-daemon-jvm.properties`, or
   `JAVA_HOME`); `micronautVersion=5.2.x`, Micronaut Gradle plugin `5.0.x`, Gradle ≥ 9.5.
2. Groovy 5 / Spock `2.4-groovy-5.0` everywhere Groovy versions are pinned (buildSrc, test deps).
3. Jackson: swap artifacts (`tools.jackson.*`), drop `jsr310`, keep a Jackson 2 BOM pin if
   anything still pulls Jackson 2 transitively, rewrite mapper setup
   to the builder API, update feature enums (`DateTimeFeature`, `EnumFeature`), custom
   (de)serializers (`ValueDeserializer`, `ValueSerializer`), annotation introspectors, exception types.
4. `jackson.*` configuration keys: `*-features` suffix, `date-time-features` for date flags.
5. `javax.annotation.PostConstruct/PreDestroy/Nullable` → `jakarta.annotation.*` / Micronaut.
6. Jackson-annotated members must be non-private in Groovy classes.
7. Enums: `;` after the last constant when members follow; fix new `@CompileStatic` inference errors.
8. Restore the Jackson 2 defaults you rely on (`allowFinalFieldsAsMutators`, `useGettersAsSetters`,
   `defaultViewInclusion`, `sortPropertiesAlphabetically`, `failOnNullForPrimitives`) — §2.1.
9. No `application-<env>.yml` duplicated between main and test resources — §2.2.
10. Replace Groovy truth-checks on `java.nio.file.Path` with `!= null` — §3.5.
11. Spock: on a `Mock`/`Stub` drop `>> null` on void-method interactions; on a `Spy` replace it
    with `>> { }`. Audit by return type, not by which specs go red — §3.6.
12. If using `@RolesAllowed`: `micronaut-security-annotations` → `micronaut-security-processor`.
   If using `@Embedded`: `@MappedProperty` or `micronaut.data.embedded.naming.strategy=LEGACY`.
   If using Turbo views: add `micronaut-views-turbo`, `@TurboView` → `@TurboStreamView`.
13. Run the suite against a Micronaut 4 baseline first so environmental failures are not mistaken
    for regressions.
