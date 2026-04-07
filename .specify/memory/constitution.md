<!--
Sync Impact Report:
- Version change: [unversioned template] → 1.0.0
- Modified principles: All (initialized from template)
- Added sections: All core principles and governance sections
- Removed sections: None
- Templates requiring updates:
  ✅ plan-template.md - Reviewed, Constitution Check section aligns
  ✅ spec-template.md - Reviewed, requirement structure aligns
  ✅ tasks-template.md - Reviewed, task organization aligns
  ✅ checklist-template.md - Reviewed, compatible with principles
- Follow-up TODOs: None
-->

# Wave Containers Constitution

## Core Principles

### I. Service-Oriented Architecture

Wave MUST maintain a clear separation of concerns through distinct service boundaries. Each service (ContainerBuildService,
ContainerMirrorService, ContainerScanService, RegistryProxyService, BlobCacheService) MUST be independently testable and
have a single, well-defined responsibility. Controllers MUST remain thin, delegating business logic to services.

**Rationale**: Microservices architecture enables independent scaling, testing, and maintenance of distinct container
provisioning capabilities. Clear boundaries prevent coupling and facilitate parallel development.

### II. Container Platform Agnosticism

Wave MUST support multiple container platforms (Docker, Kubernetes, Singularity) and registries (Docker Hub, Quay.io,
AWS ECR, Azure CR) without hardcoding platform-specific assumptions into core logic. Platform-specific strategies
MUST be isolated behind abstract interfaces.

**Rationale**: Container ecosystem diversity requires flexible provisioning that adapts to client infrastructure without
forcing migration or lock-in.

### III. Ephemeral-First Design

All container operations MUST assume ephemeral lifecycle by default. Containers, build contexts, and intermediate
artifacts MUST be automatically garbage-collected. Long-term persistence MUST be explicit opt-in through user-provided
registry credentials and push operations.

**Rationale**: Wave's core value is on-demand provisioning without manual registry management. Ephemeral-first design
prevents storage bloat and reduces operational overhead.

### IV. Proxy Transparency

When acting as a registry proxy, Wave MUST remain transparent to Docker clients and comply strictly with the Docker
Registry v2 API specification. Manifest instrumentation and layer injection MUST appear seamless to standard container
tooling.

**Rationale**: Compatibility with existing container toolchains (docker pull, kubectl, etc.) is non-negotiable for adoption.
Protocol compliance prevents breaking changes in client workflows.

### V. Async-by-Default Operations

All I/O-bound operations (registry pulls, builds, scans, blob storage) MUST use Micronaut Reactor non-blocking patterns.
Blocking calls are prohibited in HTTP request handlers. Long-running operations MUST use JobManager for background processing.

**Rationale**: Container operations involve large data transfers and external API calls. Blocking threads under load
causes cascading failures. Reactive patterns ensure resource efficiency and throughput under concurrency.

### VI. Security Scanning Integration

Container security scanning MUST be an opt-in, asynchronous operation that never blocks image provisioning. Scan results
MUST be persisted independently and queryable after container delivery. Scanning failures MUST NOT prevent container access.

**Rationale**: Security scanning adds latency. Making it non-blocking preserves Wave's primary value (fast provisioning)
while enabling security workflows for teams that require them.

### VII. Multi-Platform Build Support

Container builds MUST support cross-platform compilation (linux/amd64, linux/arm64) without requiring users to manage
build infrastructure. Platform selection MUST be declarative via API parameters. Build strategy selection (Docker vs Kubernetes)
MUST be transparent to users.

**Rationale**: ARM64 adoption in cloud and edge requires transparent multi-platform support. Kubernetes-based builds
provide scalability and isolation that local Docker daemon cannot.

## Storage & Persistence Requirements

### Data Layer Principles

- PostgreSQL MUST be used for structured metadata (build records, scan results, job state)
- Redis MUST be used for ephemeral caching and rate-limiting state
- Object storage (S3-compatible) MUST be used for blobs, layers, and build contexts
- Database migrations MUST be versioned and reversible
- No business logic in SQL; use application-layer queries via Micronaut Data JDBC

**Rationale**: Clear storage tier separation prevents cross-cutting concerns. Postgres handles ACID requirements, Redis
handles high-frequency ephemeral state, S3 handles blob scale.

### Authentication & Authorization

- Registry credentials MUST be encrypted at rest
- JWT tokens MUST be used for Tower integration authentication
- Rate limiting MUST be enforced per-user via Spillway library
- Service-to-service calls within Kubernetes MUST use pod identity where available

**Rationale**: Wave handles sensitive registry credentials and must prevent credential leakage. Rate limiting prevents
abuse of compute-intensive build operations.

## Testing & Quality Standards

### Testing Requirements

- All services MUST have unit tests using Spock 2 framework
- Integration tests MUST use Testcontainers for external dependencies (Postgres, Redis)
- Controller tests MUST mock service layer dependencies
- Test coverage MUST be measured via JaCoCo and reported after test runs
- Tests MUST NOT depend on external registries; use local registry containers or mocks

**Rationale**: Groovy + Spock provide expressive BDD-style tests. Testcontainers ensure consistent CI/CD environments.
External registry dependencies cause flaky tests and rate-limiting issues.

### Performance Standards

- HTTP endpoints MUST respond within 200ms p95 for proxy operations (excluding upstream registry latency)
- Build operations MUST queue within 100ms and report job ID
- Blob cache MUST serve layers with 90%+ hit rate under normal load
- Memory usage MUST remain under 2GB heap for typical workloads

**Rationale**: Wave sits in the critical path for container pulls. Latency directly impacts developer and CI/CD workflows.
Performance budgets prevent regressions.

## Governance

### Amendment Process

Constitution changes MUST be documented in Git commit messages with rationale. MAJOR changes (removing principles,
incompatible policy shifts) require version bump to next major. MINOR changes (new principles, expanded guidance) require
minor version bump. PATCH changes (clarifications, wording) require patch version bump.

### Compliance Review

All pull requests MUST be reviewed against constitution principles. Violations MUST be explicitly justified with
"Why Needed" and "Simpler Alternative Rejected" documentation in design docs. Unjustified complexity MUST be rejected.

### Versioning Policy

Wave follows semantic versioning (MAJOR.MINOR.PATCH):
- MAJOR: Breaking API changes, incompatible registry protocol changes
- MINOR: New features (new registry support, new build capabilities)
- PATCH: Bug fixes, performance improvements, security patches

Release process:
1. Update VERSION file
2. Update changelog.txt with changes since last release
3. Commit with `[release]` tag in first line of commit message
4. Push to upstream master branch

**Rationale**: Semantic versioning provides clear upgrade expectations. Tagged release commits enable automated CI/CD
release workflows.

**Version**: 1.0.0 | **Ratified**: 2025-10-28 | **Last Amended**: 2025-10-28
