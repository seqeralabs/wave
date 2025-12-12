# Tasks: APT Build Template

**Input**: Design documents from `/specs/251212-apt-build-template/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Included per Wave constitution testing requirements (Spock 2 framework).

**Organization**: Tasks grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Path Conventions

- **API types**: `wave-api/src/main/java/io/seqera/wave/`
- **Implementation**: `src/main/groovy/io/seqera/wave/util/`
- **Templates**: `src/main/resources/templates/`
- **Tests**: `src/test/groovy/io/seqera/wave/util/`
- **Docs**: `docs/`

---

## Phase 1: Setup (API Layer)

**Purpose**: Add new types and constants to the wave-api module

- [X] T001 [P] Add `APT_DEBIAN_V1 = "apt/debian:v1"` constant in wave-api/src/main/java/io/seqera/wave/api/BuildTemplate.java
- [X] T002 [P] Add `APT` enum value to `PackagesSpec.Type` in wave-api/src/main/java/io/seqera/wave/api/PackagesSpec.java
- [X] T003 [P] Create `AptOpts.java` configuration class in wave-api/src/main/java/io/seqera/wave/config/AptOpts.java
- [X] T004 Add `aptOpts` field and `withAptOpts()` method to `PackagesSpec` in wave-api/src/main/java/io/seqera/wave/api/PackagesSpec.java (depends on T003)

**Checkpoint**: API types ready - implementation can begin

---

## Phase 2: Foundational (Templates & Helper)

**Purpose**: Core infrastructure that MUST be complete before user stories can be tested

**CRITICAL**: No user story validation can occur until templates exist

- [X] T005 [P] Create Dockerfile template in src/main/resources/templates/apt-debian-v1/dockerfile-apt-packages.txt
- [X] T006 [P] Create Singularity template in src/main/resources/templates/apt-debian-v1/singularityfile-apt-packages.txt
- [X] T007 Create `AptHelper.groovy` with `containerFile()` method in src/main/groovy/io/seqera/wave/util/AptHelper.groovy (depends on T005, T006)
- [X] T008 Add APT dispatch logic to `containerFileFromRequest()` in src/main/groovy/io/seqera/wave/util/ContainerHelper.groovy (depends on T007)

**Checkpoint**: Foundation ready - user story implementation can now be validated

---

## Phase 3: User Story 1 - Build Container with APT Packages (Priority: P1)

**Goal**: Users can build containers with APT packages via package list or environment file

**Independent Test**: Submit container token request with `buildTemplate: "apt/debian:v1"` and packages `["curl", "wget", "git"]`, verify container has packages installed

### Tests for User Story 1

- [X] T009 [P] [US1] Create `AptHelperTest.groovy` with test for package list to Dockerfile in src/test/groovy/io/seqera/wave/util/AptHelperTest.groovy
- [X] T010 [P] [US1] Add test for package list to Singularity file in src/test/groovy/io/seqera/wave/util/AptHelperTest.groovy
- [X] T011 [P] [US1] Add test for environment file parsing (newline-separated packages) in src/test/groovy/io/seqera/wave/util/AptHelperTest.groovy
- [X] T012 [P] [US1] Add test for version-pinned packages (e.g., `nginx=1.18.0`) in src/test/groovy/io/seqera/wave/util/AptHelperTest.groovy

### Implementation for User Story 1

- [X] T013 [US1] Implement `aptPackagesToDockerFile()` method in src/main/groovy/io/seqera/wave/util/AptHelper.groovy
- [X] T014 [US1] Implement `aptPackagesToSingularityFile()` method in src/main/groovy/io/seqera/wave/util/AptHelper.groovy
- [X] T015 [US1] Implement `parseEnvironmentFile()` method for newline-separated package parsing in src/main/groovy/io/seqera/wave/util/AptHelper.groovy
- [X] T016 [US1] Verify Dockerfile template includes `DEBIAN_FRONTEND=noninteractive`, `--no-install-recommends`, and cache cleanup in src/main/resources/templates/apt-debian-v1/dockerfile-apt-packages.txt
- [X] T017 [US1] Verify Singularity template includes equivalent best practices in src/main/resources/templates/apt-debian-v1/singularityfile-apt-packages.txt

**Checkpoint**: User Story 1 complete - basic APT builds work for both Docker and Singularity

---

## Phase 4: User Story 2 - Customize APT Build (Priority: P2)

**Goal**: Users can customize base image, add base packages, and run additional commands

**Independent Test**: Submit request with `aptOpts.baseImage: "ubuntu:22.04"` and `aptOpts.commands: ["echo test"]`, verify container uses custom image and ran commands

### Tests for User Story 2

- [X] T018 [P] [US2] Add test for custom baseImage in AptOpts in src/test/groovy/io/seqera/wave/util/AptHelperTest.groovy
- [X] T019 [P] [US2] Add test for basePackages injection in src/test/groovy/io/seqera/wave/util/AptHelperTest.groovy
- [X] T020 [P] [US2] Add test for custom commands appended to Dockerfile in src/test/groovy/io/seqera/wave/util/AptHelperTest.groovy
- [X] T021 [P] [US2] Add test for custom commands appended to Singularity file in src/test/groovy/io/seqera/wave/util/AptHelperTest.groovy

### Implementation for User Story 2

- [X] T022 [US2] Add baseImage substitution support to template rendering in src/main/groovy/io/seqera/wave/util/AptHelper.groovy
- [X] T023 [US2] Add basePackages injection to package list in src/main/groovy/io/seqera/wave/util/AptHelper.groovy
- [X] T024 [US2] Add commands appending logic (RUN for Docker, %post for Singularity) in src/main/groovy/io/seqera/wave/util/AptHelper.groovy
- [X] T025 [US2] Ensure null/empty AptOpts uses defaults (baseImage: ubuntu:24.04, basePackages: ca-certificates) in src/main/groovy/io/seqera/wave/util/AptHelper.groovy

**Checkpoint**: User Story 2 complete - APT builds fully customizable

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, validation, and cleanup

- [X] T026 [P] Update docs/api.md with APT type in packages.type enum
- [X] T027 [P] Update docs/api.md with aptOpts schema documentation
- [X] T028 [P] Update docs/api.md with buildTemplate `apt/debian:v1` value
- [X] T029 [P] Add APT build example (curl request) to docs/api.md
- [X] T030 Run full test suite to verify no regressions: `./gradlew test`
- [X] T031 Build project and verify compilation: `./gradlew assemble`

**Checkpoint**: Feature complete and documented

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 completion (T001-T004)
- **User Story 1 (Phase 3)**: Depends on Phase 2 completion (T005-T008)
- **User Story 2 (Phase 4)**: Depends on Phase 3 completion (T009-T017)
- **Polish (Phase 5)**: Can start after Phase 3 for US1 docs, complete after Phase 4

### User Story Dependencies

- **User Story 1 (P1)**: Core functionality - no dependencies on other stories
- **User Story 2 (P2)**: Extends US1 with customization - depends on US1 being complete

### Within Each Phase

- Tests should be written and verified to fail before implementation
- API types (Phase 1) before implementation (Phase 2+)
- Templates before helper methods
- Helper before dispatch logic

### Parallel Opportunities

- All Phase 1 tasks (T001-T003) can run in parallel
- Templates (T005-T006) can run in parallel
- All tests within a user story can run in parallel
- All documentation tasks (T026-T029) can run in parallel

---

## Parallel Example: Phase 1 Setup

```bash
# Launch all API type tasks together:
Task: "Add APT_DEBIAN_V1 constant in wave-api/.../BuildTemplate.java"
Task: "Add APT enum value in wave-api/.../PackagesSpec.java"
Task: "Create AptOpts.java in wave-api/.../config/AptOpts.java"
```

## Parallel Example: User Story 1 Tests

```bash
# Launch all US1 tests together:
Task: "Create AptHelperTest with Dockerfile test"
Task: "Add Singularity file test"
Task: "Add environment file parsing test"
Task: "Add version-pinned packages test"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T004)
2. Complete Phase 2: Foundational (T005-T008)
3. Complete Phase 3: User Story 1 (T009-T017)
4. **STOP and VALIDATE**: Test with `./gradlew test --tests 'AptHelperTest'`
5. Basic APT builds work - can deploy for initial feedback

### Incremental Delivery

1. Phase 1 + Phase 2 → Foundation ready
2. Add User Story 1 → Test → Basic APT builds work (MVP!)
3. Add User Story 2 → Test → Full customization support
4. Add Documentation → Feature complete

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Follow CranHelper pattern for implementation structure
- Use TemplateRenderer for variable substitution in templates
- Commit after each logical task group
- Run `./gradlew test` frequently to catch regressions

---

## Summary

| Metric | Value |
|--------|-------|
| Total Tasks | 31 |
| Phase 1 (Setup) | 4 tasks |
| Phase 2 (Foundational) | 4 tasks |
| Phase 3 (User Story 1) | 9 tasks |
| Phase 4 (User Story 2) | 8 tasks |
| Phase 5 (Polish) | 6 tasks |
| Parallel Opportunities | 18 tasks marked [P] |
| MVP Scope | Phases 1-3 (17 tasks) |
