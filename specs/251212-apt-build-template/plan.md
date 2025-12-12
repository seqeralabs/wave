# Implementation Plan: APT Build Template

**Branch**: `251212-apt-build-template` | **Date**: 2025-12-12 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/251212-apt-build-template/spec.md`

## Summary

Add an `apt/debian:v1` build template to Wave that allows users to build containers with Debian/Ubuntu APT packages. This extends the existing build template system (which supports conda/pixi/cran) to support system-level package installation via APT. The implementation follows the established patterns used by CondaHelper, PixiHelper, and CranHelper.

## Technical Context

**Language/Version**: Java 21+ / Groovy 4.x
**Primary Dependencies**: Micronaut 4.x, Netty, Reactor
**Storage**: PostgreSQL (build records), Redis (caching)
**Testing**: Spock 2 framework with Testcontainers
**Target Platform**: Linux server (Kubernetes/Docker)
**Project Type**: Existing microservice extension
**Performance Goals**: Build queue within 100ms, proxy operations <200ms p95
**Constraints**: Non-blocking I/O required, follows existing template patterns
**Scale/Scope**: Extension to existing system - 6-8 new/modified files

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Service-Oriented Architecture | PASS | Extends existing ContainerHelper dispatch; AptHelper isolated |
| II. Container Platform Agnosticism | PASS | Supports Docker and Singularity via templates |
| III. Ephemeral-First Design | PASS | No new persistence requirements |
| IV. Proxy Transparency | N/A | Not a proxy feature |
| V. Async-by-Default Operations | PASS | Uses existing async build pipeline |
| VI. Security Scanning Integration | N/A | No changes to scanning |
| VII. Multi-Platform Build Support | PASS | Templates work for amd64/arm64 |
| Testing Requirements | PASS | Will use Spock + Testcontainers pattern |
| Performance Standards | PASS | No new latency paths introduced |

## Project Structure

### Documentation (this feature)

```text
specs/251212-apt-build-template/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── api-changes.md   # API contract changes
├── checklists/
│   └── requirements.md  # Specification quality checklist
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (repository root)

```text
wave-api/src/main/java/io/seqera/wave/
├── api/
│   ├── BuildTemplate.java           # [MODIFY] Add APT_DEBIAN_V1 constant
│   └── PackagesSpec.java            # [MODIFY] Add APT type, aptOpts field
└── config/
    └── AptOpts.java                 # [NEW] APT build options

src/main/groovy/io/seqera/wave/util/
├── ContainerHelper.groovy           # [MODIFY] Add APT dispatch logic
└── AptHelper.groovy                 # [NEW] APT template rendering

src/main/resources/templates/
└── apt-debian-v1/                   # [NEW] Template directory
    ├── dockerfile-apt-packages.txt
    └── singularityfile-apt-packages.txt

src/test/groovy/io/seqera/wave/util/
└── AptHelperTest.groovy             # [NEW] Unit tests

docs/
└── api.md                           # [MODIFY] Add APT build template documentation
                                     # - Add aptOpts to request schema
                                     # - Add APT to packages.type enum
                                     # - Add apt/debian:v1 example
                                     # - Add apt/debian:v1 to buildTemplate values
```

**Structure Decision**: Follows existing Wave conventions - API types in `wave-api/`, implementation in `src/main/groovy/`, templates in `src/main/resources/templates/`. Documentation follows existing guide structure.

## Complexity Tracking

> No constitution violations requiring justification.

| Check | Result |
|-------|--------|
| New services required | None - extends existing |
| New persistence entities | None - uses existing BuildRecord |
| Breaking API changes | None - additive only |
| External dependencies | None - APT is in base images |
