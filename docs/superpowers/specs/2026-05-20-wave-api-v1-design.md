# Wave API v1 — Design

**Date:** 2026-05-20
**Status:** Draft, pending review
**Author:** brainstorm session with Paolo
**Owner:** Paolo Di Tommaso

## Goal

Consolidate Wave's REST API into a stable v1 surface, with TypeSpec as the source of truth for the schema. The new API lives under the `/w1` prefix, is generated from a single TypeSpec spec, and reuses Wave's existing service layer unchanged. Existing `/v1alpha1`, `/v1alpha2`, `/v1alpha3` and unversioned endpoints remain functional and are marked as deprecated.

## Non-goals

- No semantic changes to Wave services. Build/mirror/scan/inspect behaviors are unchanged.
- No changes to the registry proxy (`/v2/*`). It implements the Docker Registry v2 protocol and is out of scope.
- No changes to metrics endpoints (`/v1alpha2/metrics/*`, `/v1alpha3/metrics/*`). They remain on alpha; v1 does not include them.
- No replacement of the existing `wave-api` module. It stays in place for backward compatibility with the in-the-wild Nextflow / Tower clients that consume the alpha endpoints.
- No new error envelope (RFC 7807 problem+json), pagination, or status-enum cleanup. Faithful rename only.

## Approach summary

| Concern              | Decision                                                                                  |
|----------------------|-------------------------------------------------------------------------------------------|
| URL prefix           | `/w1` — avoids collision with Docker Registry v1/v2 paths and brands Wave clearly         |
| Source of truth      | TypeSpec (`wave-api-v1/spec/main.tsp`)                                                    |
| OpenAPI generation   | `tsp compile` → versioned YAML in `wave-api-v1/build/openapi/`                            |
| Java codegen         | `io.micronaut.openapi` Gradle plugin in `wave/` main app, server-side mode                |
| Generated DTOs       | Committed back into `wave-api-v1/src/main/java/io/seqera/wave/api/v1/model/`              |
| Generated interfaces | Committed back into `wave-api-v1/src/main/java/io/seqera/wave/api/v1/spec/`               |
| Service reuse        | New controllers delegate to existing `ContainerRequestService`, etc. — zero service edits |
| Alpha compatibility  | Alpha endpoints kept, mark responses with `Deprecation: true` + `Sunset` HTTP headers     |
| API scope            | Container, builds, mirrors, scans, inspect, validate-creds, service-info                  |
| Out of v1            | Metrics, registry proxy (`/v2/*`), view controller                                        |

## Module layout

```
wave/
├─ wave-api/                     # EXISTING — unchanged
│  └─ src/main/java/io/seqera/wave/api/        # hand-written DTOs (Moshi) — alpha clients
│
├─ wave-api-v1/                  # NEW
│  ├─ spec/
│  │  └─ main.tsp                              # TypeSpec source of truth
│  ├─ package.json                             # @typespec/compiler, http, rest, openapi3
│  ├─ tspconfig.yaml                           # emits openapi-{version}.yaml
│  ├─ build.gradle                             # runs npm install + tsp compile
│  ├─ build/openapi/openapi-{version}.yaml     # generated YAML (not committed)
│  └─ src/main/java/io/seqera/wave/api/v1/
│     ├─ model/                                # generated DTOs (COMMITTED)
│     └─ spec/                                 # generated server interfaces (COMMITTED)
│
└─ src/main/groovy/io/seqera/wave/
   ├─ controller/                              # alpha controllers remain unchanged
   │  ├─ ContainerController.groovy            # +Deprecation header on alpha routes
   │  ├─ BuildController.groovy                # +Deprecation header
   │  ├─ MirrorController.groovy               # +Deprecation header
   │  ├─ ScanController.groovy                 # +Deprecation header
   │  ├─ InspectController.groovy              # +Deprecation header
   │  ├─ ValidateController.groovy             # +Deprecation header
   │  ├─ ServiceInfoController.groovy          # +Deprecation header on /service-info
   │  └─ v1/                                   # NEW v1 controllers
   │     ├─ ContainersV1Controller.groovy
   │     ├─ BuildsV1Controller.groovy
   │     ├─ MirrorsV1Controller.groovy
   │     ├─ ScansV1Controller.groovy
   │     ├─ InspectionsV1Controller.groovy
   │     ├─ CredentialsV1Controller.groovy
   │     └─ ServiceInfoV1Controller.groovy
   └─ service/                                 # UNCHANGED
```

`wave-api-v1` is published as a Java library jar. External clients depend on it for v1 DTOs. The main `wave/` application depends on it for the generated server-side interfaces that the new `*V1Controller` classes implement.

## URL surface — alpha → v1 mapping

All v1 routes are JSON over HTTPS. Authentication, rate limiting, and headers behave identically to alpha equivalents.

| Resource          | Method | v1 path                           | Existing alpha path                          | Notes                                       |
|-------------------|--------|-----------------------------------|----------------------------------------------|---------------------------------------------|
| Container request | POST   | `/w1/containers`                  | `POST /v1alpha2/container`, `/container-token` | Body unchanged: `SubmitContainerTokenRequest` |
| Container request | GET    | `/w1/containers/{id}`             | `GET /v1alpha2/container/{requestId}`        | Same response shape                         |
| Container status  | GET    | `/w1/containers/{id}/status`      | `GET /v1alpha2/container/{requestId}/status` | Same response shape                         |
| Container request | DELETE | `/w1/containers/{id}`             | `DELETE /container-token/{token}`            | No alpha-versioned equivalent existed       |
| Build             | GET    | `/w1/builds/{id}`                 | `GET /v1alpha1/builds/{buildId}`             | Same response shape                         |
| Build status      | GET    | `/w1/builds/{id}/status`          | `GET /v1alpha1/builds/{buildId}/status`      |                                             |
| Build logs        | GET    | `/w1/builds/{id}/logs`            | `GET /v1alpha1/builds/{buildId}/logs`        | `text/plain` response                       |
| Build conda lock  | GET    | `/w1/builds/{id}/condalock`       | `GET /v1alpha1/builds/{buildId}/condalock`   | `text/plain` response                       |
| Mirror            | GET    | `/w1/mirrors/{id}`                | `GET /v1alpha1/mirrors/{mirrorId}`           |                                             |
| Mirror logs       | GET    | `/w1/mirrors/{id}/logs`           | `GET /v1alpha1/mirrors/{mirrorId}/logs`      | `text/plain`                                |
| Scan              | POST   | `/w1/scans`                       | `POST /scans`                                | New v1 also gets a stable POST path         |
| Scan              | GET    | `/w1/scans/{id}`                  | `GET /v1alpha1/scans/{scanId}`               |                                             |
| Scan logs         | GET    | `/w1/scans/{id}/logs`             | `GET /v1alpha1/scans/{scanId}/logs`          | `text/plain`                                |
| Scan SPDX         | GET    | `/w1/scans/{id}/spdx`             | `GET /v1alpha1/scans/{scanId}/spdx`          | `application/json` SPDX format              |
| Inspection        | POST   | `/w1/inspections`                 | `POST /v1alpha1/inspect`                     | Synchronous; returns inspection result      |
| Credentials check | POST   | `/w1/credentials/validate`        | `POST /v1alpha2/validate-creds`              |                                             |
| Service info      | GET    | `/w1/service-info`                | `GET /service-info`                          |                                             |

The DTO payload shapes are kept byte-identical to current alpha responses. The TypeSpec spec defines models that match the existing `wave-api` classes field for field, so service-layer adapters are pure renames with no field transformation.

## TypeSpec spec organization

Single file `wave-api-v1/spec/main.tsp` (matches sched-api). Structure:

```typespec
import "@typespec/http";
import "@typespec/rest";
import "@typespec/openapi3";
import "@typespec/versioning";

using TypeSpec.Http;
using TypeSpec.Rest;
using TypeSpec.Versioning;

@service(#{ title: "Wave API" })
@versioned(Versions)
namespace WaveApi;

enum Versions {
  @doc("1.0.0") v1: "1.0.0",
}

// ----- Shared types: enums, error responses
enum ContainerStatus { ... }
enum BuildStatus { ... }
enum ScanStatus { ... }
enum MirrorStatus { ... }
enum ScanMode { ... }
enum ScanLevel { ... }
enum ImageNameStrategy { ... }
model ErrorResponse { message: string; }

// ----- Resource models
model ContainerRequest { ... }
model ContainerResponse { ... }
model ContainerStatusResponse { ... }
model BuildResponse { ... }
model BuildStatusResponse { ... }
model MirrorResponse { ... }
model ScanRequest { ... }
model ScanResponse { ... }
model SpdxDocument { ... }
model InspectRequest { ... }
model InspectResponse { ... }
model ValidateCredsRequest { ... }
model ValidateCredsResponse { ... }
model ServiceInfoResponse { ... }

// ----- API interfaces
@route("/w1/containers")   interface ContainersApi   { ... }
@route("/w1/builds")       interface BuildsApi       { ... }
@route("/w1/mirrors")      interface MirrorsApi      { ... }
@route("/w1/scans")        interface ScansApi        { ... }
@route("/w1/inspections")  interface InspectionsApi  { ... }
@route("/w1/credentials")  interface CredentialsApi  { ... }
@route("/w1/service-info") interface ServiceInfoApi  { ... }
```

The model field names, types, and optionality mirror the current Java DTOs in `wave-api/src/main/java/io/seqera/wave/api/` (`SubmitContainerTokenRequest`, `ContainerStatusResponse`, `BuildStatusResponse`, `ContainerInspectRequest`, etc.). Where current DTOs use Groovy classes in `src/main/groovy/io/seqera/wave/exchange/` or controller-local request classes (e.g. `ValidateRegistryCredsRequest.groovy`), the TypeSpec model carries the same fields.

## Codegen pipeline (Gradle)

### `wave-api-v1/build.gradle`

Mirrors `sched-api/build.gradle`:

1. `installTypeSpec` (Exec) — `npm install` to fetch the TypeSpec compiler.
2. `generateOpenApi` (Exec, depends on installTypeSpec) — runs `npx tsp compile spec/main.tsp` → `build/openapi/openapi-1.0.0.yaml` + `openapi-latest.yaml`.
3. Standard `java-library-conventions` compiles the committed sources under `src/main/java/io/seqera/wave/api/v1/`.

The module also depends (at compile time) on the Micronaut serde and Jakarta validation annotations the generated code uses.

### `wave/build.gradle` (main app)

Add `io.micronaut.openapi` plugin and configure server-side codegen against the wave-api-v1 YAML:

```groovy
openapi {
  server('v1', project(':wave-api-v1').file('build/openapi/openapi-latest.yaml')) {
    apiPackageName = 'io.seqera.wave.api.v1.spec'
    modelPackageName = 'io.seqera.wave.api.v1.model'
    useReactive = true
    fluxForArrays = false
    serializationLibrary = 'MICRONAUT_SERDE_JACKSON'
  }
}
```

Plus a copy task that takes the generated sources out of `build/generated/openapi/...` and copies them into `wave-api-v1/src/main/java/io/seqera/wave/api/v1/{spec,model}/`. This mirrors sched-app's `cleanApiCode` + copy pattern exactly. The copy makes the generated code source-controlled so:

- Downstream clients consuming `wave-api-v1` get DTOs without running TypeSpec.
- IDEs auto-import and code-complete on real source files.
- The OpenAPI generation step is not on the developer's hot path during normal compilation.

The copy task runs as part of `./gradlew assemble`. Developers regenerate manually after editing `main.tsp`.

## Controller migration

Each v1 controller is a thin Groovy adapter that:

1. Implements the generated API interface (`io.seqera.wave.api.v1.spec.ContainersApi`, etc.).
2. Injects the same existing services that the alpha controllers use (`ContainerRequestService`, `ContainerBuildService`, `ContainerMirrorService`, `ContainerScanService`, `ContainerInspectService`, `ValidationService`, `BuildLogService`, etc.).
3. Maps the v1 model classes to/from the current internal model classes via small mapper methods (or constructors). Since v1 DTO fields are intentionally identical to current ones, mappers are mechanical copy assignments.
4. Returns the same HTTP statuses and error envelopes as the alpha equivalent.

**Example shape:**

```groovy
@Controller
class ContainersV1Controller implements ContainersApi {

    @Inject ContainerRequestService containerService
    @Inject UserService userService
    // ... same injects as ContainerController

    @Override
    HttpResponse<ContainerResponse> createContainer(ContainerRequest req, HttpRequest httpReq) {
        // Adapt v1 request → existing internal SubmitContainerTokenRequest
        def internal = ContainersV1Mapper.toInternal(req)
        // Delegate to the exact same path the alpha controller uses
        def result = doRunRequestInternal(internal, httpReq)
        // Adapt internal response → v1 response
        return HttpResponse.ok(ContainersV1Mapper.toV1(result))
    }
}
```

The mapper methods are 1-to-1 field copies; if a field has the same name/type in both worlds the mapper can use a constructor or builder for clarity.

The shared request handling logic currently sitting inside `ContainerController.groovy` private methods (`doRunRequestInternal`, etc.) gets extracted into a dedicated package-private collaborator (e.g. `ContainerRequestHandler`) that both `ContainerController` (alpha) and `ContainersV1Controller` call. This is the only refactor inside `wave/` and is local to the controller package — it does not touch services.

## Backward compatibility

Existing alpha controllers and unversioned routes remain functional. Each alpha route response is augmented with two HTTP headers:

- `Deprecation: true`
- `Sunset: Sat, 31 Dec 2026 23:59:59 GMT` (placeholder — exact date set when v1 ships)

Implementation: a single Micronaut `HttpServerFilter` that matches paths under `/v1alpha1/**`, `/v1alpha2/**`, `/v1alpha3/**` and the well-known unversioned routes (`/container-token`, `/container-token/**`, `/service-info`, `/validate-creds`, `/scans`), and stamps both headers on the outbound response. Lives in `src/main/groovy/io/seqera/wave/filter/`.

No client-facing breakage. Nextflow and Tower keep working as-is. The OpenAPI YAML for v1 is the only schema documented going forward; the alpha endpoints are explicitly excluded from the new spec.

## Testing

- **wave-api-v1**: Spock tests that round-trip a handful of DTOs through Jackson serde to lock the wire format. Sized to match `sched-api/src/test/groovy/`.
- **Controllers** (`wave/src/test/groovy/.../controller/v1/`): per controller, one Spock test per route that uses a Micronaut `@MicronautTest` slice + mocked service dependencies. Asserts:
  - HTTP status, content type, response body shape (via JSON comparison)
  - That the controller calls the right service method with the right arguments
- **Parallel-with-alpha**: for each v1 endpoint, a side-by-side test that submits the same canonical request to both the v1 and alpha endpoints and asserts the response payloads are equivalent (modulo field renames). This is the single most important guard against semantic drift.
- **Filter test**: integration test verifying `Deprecation`/`Sunset` headers appear on alpha responses and *not* on v1 responses.

## Risks and open questions

- **DTO field name drift between TypeSpec and current Java models.** Hand-checking required during spec authoring. The parallel-with-alpha tests catch any drift at runtime.
- **The Micronaut OpenAPI plugin's generated style** (reactive vs imperative, validation annotations, Optional vs nullable). Need to match what `sched-app` uses — `useReactive = true`, `serializationLibrary = MICRONAUT_SERDE_JACKSON`. Confirm during scaffolding.
- **Sunset date.** Need a stakeholder call on how long alphas are supported. Provisionally 12 months from v1 GA. Not blocking the design.
- **Existing `wave-api` module versioning.** It stays at its current version and continues to be released; no new feature work lands in it. Documented but not changed.

## Out of scope (explicit list)

- Registry proxy (`/v2/*`) — Docker Registry protocol, not REST API.
- Metrics endpoints (`/v1alpha2/metrics/*`, `/v1alpha3/metrics/*`) — observability surface, separate concern.
- View controller (`/view/**`) — HTML pages, not API.
- Error envelope redesign, pagination, status-enum cleanup — deferred to a future v1.x or v2.
- Migrating Nextflow client to v1 — orthogonal effort, owned by the Nextflow team.

## Acceptance criteria

1. `wave-api-v1` module exists, builds standalone, publishes a jar with the v1 DTOs and API interfaces.
2. Running `./gradlew :wave-api-v1:generateOpenApi` produces `build/openapi/openapi-1.0.0.yaml` and `openapi-latest.yaml`.
3. Running `./gradlew :wave:compileJava` regenerates the Micronaut server interfaces and DTOs into `wave-api-v1/src/main/java/io/seqera/wave/api/v1/`.
4. All `/w1/*` endpoints from the URL surface table respond with the same payloads as their alpha counterparts (verified by parallel tests).
5. Alpha endpoints carry `Deprecation: true` + `Sunset` headers; v1 endpoints do not.
6. No existing test in `wave/src/test/` is modified or skipped.
7. OpenAPI YAML for v1 is exposed at `/openapi/openapi-latest.yaml` and rendered in the Swagger UI under `/openapi/index.html` (matching app-bootstrap's pattern).
