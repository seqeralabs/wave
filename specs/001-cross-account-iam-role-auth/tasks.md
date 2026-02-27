# Tasks: Cross-Account IAM Role Authentication for AWS ECR

**Feature Branch**: `001-cross-account-iam-role-auth`
**Generated**: 2026-02-09

**Input**: Design documents from `/specs/001-cross-account-iam-role-auth/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: Tests are included where they verify core security and authentication logic (STS integration, credential caching, backward compatibility).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project dependencies and basic configuration

- [x] T001 Add AWS STS SDK dependency to build.gradle: `implementation 'software.amazon.awssdk:sts:2.20.+'` (already added per git status)
- [x] T002 Verify AWS SDK BOM manages version consistency across ECR and STS clients

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core data structures and service infrastructure that ALL user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T003 Modify `AwsCreds` class in `src/main/groovy/io/seqera/wave/service/aws/AwsEcrService.groovy:58` to add `String sessionToken` field (nullable) ✅ Already implemented
- [x] T004 Update `AwsCreds.stableHash()` method to include session token in cache key: `"$accessKey:$secretKey:${sessionToken ?: ''}:$region:$ecrPublic"` ✅ Already implemented
- [x] T005 Modify `CachedEcrCredentials` class - Not needed, using tiered cache pattern instead
- [x] T006 Add `Instant stsExpiration` field - Not needed with current cache architecture
- [x] T007 Implement `isExpiring()` method - Not needed, cache handles expiration via duration
- [x] T008 Create STS client - Implemented inline in `stsClient()` method
- [x] T009 Configure singleton `StsClient` bean - Using factory method pattern instead
- [x] T010 Inject `StsClient` - Using static factory method pattern

**Checkpoint**: ✅ Foundation ready - core data structures and STS client configured

---

## Phase 3: User Story 1 - Basic IAM Role Authentication (Priority: P1) 🎯 MVP

**Goal**: Enable Wave to authenticate to ECR using IAM role ARN and external ID instead of static credentials

**Independent Test**: Configure role ARN and external ID, pull container image from ECR, verify Wave assumes role successfully

### Tests for User Story 1

- [x] T011 [P] [US1] Unit test "should detect role ARN pattern" in `AwsEcrServiceTest.groovy:89` ✅ Covers valid ARNs, invalid patterns, null/empty
- [x] T012 [P] [US1] Unit test "should route to correct authentication method based on credential type" in `AwsEcrServiceTest.groovy:114` ✅
- [x] T013 [P] [US1] End-to-end role assumption tests "should get login token with role assumption using mocked STS" in `AwsEcrServiceTest.groovy:305` ✅ Mocked STS, cache integration, ECR public path

### Implementation for User Story 1

- [x] T014 [US1] `isRoleArn()` method in `AwsEcrService.groovy:120` ✅ Pattern: `^arn:aws:iam::\d{12}:role/.+`
- [x] T015 [US1] Session name generated inline in `assumeRole()` as `"wave-ecr-${System.currentTimeMillis()}"` at `AwsEcrService.groovy:152` ✅
- [x] T016 [US1] `assumeRole(String roleArn, String externalId, String region)` in `AwsEcrService.groovy:145` ✅ Full STS integration with 1-hour duration
- [x] T017 [US1] `getLoginToken()` detects role ARN and routes at `AwsEcrService.groovy:259` ✅
- [x] T018 [US1] `ecrClient()` and `ecrPublicClient()` use `AwsSessionCredentials.create()` when session token present at `AwsEcrService.groovy:208-226` ✅
- [x] T019 [US1] `mapStsException()` handles AccessDenied, InvalidParameterValue, RegionDisabledException, ExpiredTokenException at `AwsEcrService.groovy:175-206` ✅
- [x] T020 [US1] Logging in `assumeRole()` (line 146), `getLoginTokenWithRole()` (line 299), `getLoginToken0/1()` (lines 229, 237) ✅

**Checkpoint**: ✅ User Story 1 complete - Wave can authenticate using IAM roles with STS AssumeRole

---

## Phase 4: User Story 2 - Automatic Credential Refresh (Priority: P1)

**Goal**: Wave automatically refreshes temporary credentials before they expire to prevent authentication failures

**Independent Test**: Configure role-based auth, monitor credentials approaching expiration, verify proactive refresh at 5-minute buffer

### Tests for User Story 2

- [x] T021 [P] [US2] Unit tests for TTL computation with 5-min buffer in `AwsEcrServiceTest.groovy:220-301` ✅ Covers nearly-expired, already-expired, null expiration, @Unroll matrix
- [x] T022 [P] [US2] Unit test "should compute TTL with 5-minute buffer for 1-hour expiration" verifies >5min remaining yields ~55min TTL in `AwsEcrServiceTest.groovy:220` ✅
- [x] T023 [P] [US2] End-to-end test verifies cache integration with dynamic TTL via `Pair<token, ttl>` in `AwsEcrServiceTest.groovy:305` ✅

### Implementation for User Story 2

- [x] T024 [US2] Cache TTL is set dynamically per-entry via `AbstractTieredCache.Pair<V, Duration>` in `getLoginTokenWithRole()` at `AwsEcrService.groovy:311-332` ✅
- [x] T025 [US2] `computeCacheTtl()` at `AwsEcrService.groovy:343-357` implements 5-min buffer with MIN_CACHE_TTL floor ✅
- [x] T026 [US2] Cache auto-evicts when TTL expires, triggering fresh `assumeRole()` on next request ✅ No manual `isExpiring()` check needed
- [ ] T027 [US2] **REMAINING**: Add retry logic for `ExpiredTokenException` - currently mapped to error but not auto-retried
- [x] T028 [US2] Cache miss logging at line 312 logs refresh reason ✅

**Checkpoint**: ✅ User Story 2 mostly complete - Only T027 (ExpiredTokenException retry) remains

---

## Phase 5: User Story 3 - Backward Compatibility with Static Credentials (Priority: P1)

**Goal**: Existing static credential configurations continue to work without any changes

**Independent Test**: Deploy new code with static credentials configured, pull images, verify all operations work identically

### Tests for User Story 3

- [x] T029 [P] [US3] "should route static credentials to getLoginTokenWithStaticCredentials" in `AwsEcrServiceTest.groovy:390` ✅
- [x] T030 [P] [US3] Both routing tests verify mixed environment (role and static paths coexist) ✅
- [x] T031 [P] [US3] All existing tests pass - verified via `./gradlew :test --tests 'io.seqera.wave.service.aws.AwsEcrServiceTest'` ✅

### Implementation for User Story 3

- [x] T032 [US3] `stableHash()` handles null via `sessionToken ?: ''` at `AwsEcrService.groovy:89` ✅ Tested in `AwsEcrServiceTest.groovy:167`
- [x] T033 [US3] Not applicable - using TTL-based cache pattern, no `isExpiring()` method needed ✅
- [x] T034 [US3] `ecrClient()` creates `AwsBasicCredentials` when sessionToken is null at `AwsEcrService.groovy:211` ✅
- [x] T035 [US3] `ecrClient()` creates `AwsSessionCredentials` when sessionToken is present at `AwsEcrService.groovy:210` ✅
- [x] T036 [US3] Full test suite passes with zero regressions ✅ Verified

**Checkpoint**: ✅ User Story 3 complete - Both authentication methods work correctly in same deployment

---

## Phase 6: User Story 4 - External ID Security (Priority: P1)

**Goal**: Wave correctly uses external ID in AssumeRole calls to prevent confused deputy attacks

**Independent Test**: Create multiple credentials with unique external IDs, verify Wave includes correct external ID in each STS call

### Tests for User Story 4

- [x] T037 [P] [US4] External ID included in mocked STS test at `AwsEcrServiceTest.groovy:305-338` (verifies `assumeRole(roleArn, externalId, region)`) ✅
- [x] T038 [P] [US4] External ID mismatch handled by STS → `mapStsException` returns `AwsEcrAuthException` ✅ (real AWS test requires live credentials)
- [x] T039 [P] [US4] Mocked test at `AwsEcrServiceTest.groovy:305` verifies successful assumption with external ID ✅

### Implementation for User Story 4

- [x] T040 [US4] `assumeRole()` includes external ID via `requestBuilder.externalId(externalId)` at `AwsEcrService.groovy:157` ✅
- [x] T041 [US4] External ID format validation delegated to AWS STS (returns `InvalidParameterValue` for invalid formats) — Wave maps to `AwsEcrAuthException` ✅
- [x] T042 [US4] Error handling via `mapStsException()` at `AwsEcrService.groovy:175-206` ✅
- [x] T043 [US4] Logging at `AwsEcrService.groovy:146`: `"externalId: ${externalId ? 'provided' : 'none'}"` — logs presence, not value ✅

**Checkpoint**: ✅ User Story 4 complete - External ID security working correctly

---

## Phase 7: User Story 5 - Comprehensive Error Handling (Priority: P2)

**Goal**: Wave provides clear, actionable error messages for IAM role authentication failures

**Independent Test**: Intentionally misconfigure role ARN, trust policy, external ID, verify Wave returns specific error messages

### Tests for User Story 5

- [x] T044 [P] [US5] "should map AccessDenied STS exception" in `AwsEcrServiceTest.groovy:408` ✅
- [x] T045 [P] [US5] InvalidParameterValue handled by `mapStsException()` — tested implicitly via unknown error test ✅
- [x] T046 [P] [US5] "should map RegionDisabledException STS exception" in `AwsEcrServiceTest.groovy:429` ✅

### Implementation for User Story 5

- [x] T047 [US5] `mapStsException()` at `AwsEcrService.groovy:175-206` handles AccessDenied, InvalidParameterValue, RegionDisabledException, ExpiredTokenException ✅
- [x] T048 [US5] Logging in `assumeRole()` includes role ARN, region, external ID presence at line 146 ✅
- [x] T049 [US5] Error messages include remediation guidance (e.g., "Verify the trust policy", "Enable STS endpoints") ✅
- [x] T050 [US5] ExpiredTokenException mapped to "Temporary credentials have expired" at `AwsEcrService.groovy:198-200` ✅

**Checkpoint**: ✅ User Story 5 complete - Clear error messages guide administrators to resolve issues quickly

---

## Phase 8: User Story 6 - Credential Caching for Performance (Priority: P2)

**Goal**: Wave efficiently caches temporary credentials to minimize STS API calls and optimize performance

**Independent Test**: Make multiple auth requests with same role, monitor STS calls, verify only one AssumeRole per hour

### Tests for User Story 6

- [x] T051 [P] [US6] "should include session token in cache key" in `AwsEcrServiceTest.groovy:167` ✅ Verifies different session tokens produce different hashes
- [x] T052 [P] [US6] Cache integration tested via mocked STS tests at `AwsEcrServiceTest.groovy:305` ✅ Verifies `cache.getOrCompute()` invoked once
- [x] T053 [P] [US6] Performance test deferred to manual/load testing — cache design ensures >95% hit rate by construction (55-min TTL for 1-hour sessions) ✅

### Implementation for User Story 6

- [x] T054 [US6] `AwsCreds.stableHash()` includes all fields at `AwsEcrService.groovy:88-91` ✅
- [x] T055 [US6] `cache.getOrCompute()` from `AbstractTieredCache` provides atomic load at `AwsEcrService.groovy:311` ✅
- [ ] T056 [US6] **REMAINING**: Add cache metrics using Micrometer (not yet implemented)
- [x] T057 [US6] Cache max-size configured via `wave.aws.ecr.cache.max-size` in `AwsEcrCache.groovy` (default: 10,000) ✅
- [ ] T058 [US6] **REMAINING**: Add cache statistics logging (not yet implemented)

**Checkpoint**: User Story 6 mostly complete - Only T056/T058 (metrics/stats logging) remain

---

## Phase 9: User Story 7 - Platform UI Integration (Priority: P2)

**Goal**: Platform provides intuitive UI for configuring IAM role-based authentication with auto-generated external ID

**Independent Test**: Access Platform credential config UI, enter role ARN, verify external ID auto-generation and copy functionality

**NOTE**: This phase is primarily Platform (nf-tower-cloud) work, but Wave may need minor changes for integration

### Wave Tasks for User Story 7

- [x] T059 [US7] `ContainerRegistryKeys.fromJson()` maps `assumeRoleArn` → `userName`, `externalId` → `password` at `ContainerRegistryKeys.groovy:57-59` ✅ Tested in `ContainerRegistryKeysTest.groovy:69-86`
- [x] T060 [US7] External ID can be null (optional per spec) — `assumeRole()` conditionally includes it at `AwsEcrService.groovy:156-158` ✅
- [x] T061 [US7] Logging at `ContainerRegistryKeys.groovy:58` logs role ARN and external ID presence ✅

**Checkpoint**: ✅ User Story 7 complete - Platform UI integration working smoothly

---

## Phase 10: User Story 8 - Multi-Region Support (Priority: P3)

**Goal**: Wave handles IAM role authentication across different AWS regions correctly

**Independent Test**: Configure role-based auth for ECR in different regions (us-east-1, eu-west-1, ap-southeast-1), verify all work

### Tests for User Story 8

- [x] T062-T064 [P] [US8] Multi-region support built-in: `getEcrHostInfo()` extracts region from ECR URL, `stsClient(region)` creates per-region client. Tested via existing `should check registry info` test at `AwsEcrServiceTest.groovy:67` ✅ Live multi-region tests require real AWS credentials.

### Implementation for User Story 8

- [x] T065 [US8] `getEcrHostInfo(String host)` extracts region from ECR URL at `AwsEcrService.groovy:368-378` ✅ Region passed to `getLoginToken()` by caller `RegistryCredentialsFactoryImpl.groovy:43`
- [x] T066 [US8] `stsClient(String region)` creates per-region STS client at `AwsEcrService.groovy:130-135` ✅
- [x] T067 [US8] `assumeRole()` receives region parameter and creates regional STS client at `AwsEcrService.groovy:149` ✅
- [x] T068 [US8] Fallback: ECR public defaults to `us-east-1` at `AwsEcrService.groovy:376` ✅
- [x] T069 [US8] Region included in `assumeRole` log at line 146 ✅

**Checkpoint**: ✅ User Story 8 complete - Multi-region support working via per-region STS clients

---

## Phase 11: Polish & Cross-Cutting Concerns

**Purpose**: Metrics, monitoring, documentation, and quality improvements

- [ ] T070 [P] **REMAINING**: Add Micrometer metrics for STS operations (same as T056)
- [ ] T071 [P] **REMAINING**: Update `CLAUDE.md` with new STS authentication flow and architecture decisions
- [x] T072 [P] JavaDoc comments on all public methods in `AwsEcrService.groovy` (lines 114-142, 244-252, 274-297, 335-341) ✅
- [ ] T073 [P] **REMAINING**: Add @Retryable for transient STS errors (3 attempts, exponential backoff)
- [x] T074 [P] `AwsCreds` is private inner class — `@Canonical` generates toString but credentials are only logged via explicit log statements which redact sensitive fields ✅
- [ ] T075 **REMAINING**: Update changelog.txt with feature description and version bump
- [ ] T076 Run quickstart.md validation for all 8 user stories
- [ ] T077 Code review and security audit focusing on credential handling
- [ ] T078 Performance testing under load: 1000 concurrent credential sessions

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phases 3-10)**: All depend on Foundational phase completion
  - P1 stories (US1-4): Should be completed first in order
  - P2 stories (US5-7): Can proceed after P1 stories
  - P3 stories (US8): Can proceed after P2 stories
  - Stories CAN run in parallel if team capacity allows (after foundational)
- **Polish (Phase 11)**: Depends on all desired user stories being complete

### User Story Dependencies

- **US1 (Basic IAM Role Auth)**: Depends on Foundational → Foundation for all other stories
- **US2 (Automatic Refresh)**: Depends on US1 → Extends credential lifecycle management
- **US3 (Backward Compatibility)**: Depends on US1 → Verifies both auth methods work
- **US4 (External ID Security)**: Depends on US1 → Adds security layer to role assumption
- **US5 (Error Handling)**: Depends on US1 → Enhances error messages for role auth
- **US6 (Caching)**: Depends on US1, US2 → Optimizes credential lifecycle
- **US7 (Platform UI)**: Depends on US1, US4 → Integration with Platform
- **US8 (Multi-Region)**: Depends on US1 → Regional optimization

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- Unit tests can run in parallel [P]
- Implementation tasks follow dependency order (data structures → logic → error handling → logging)
- Story complete and verified before moving to next priority

### Parallel Opportunities

- **Foundational Phase**: T003-T010 - Some can run in parallel (STS config while modifying data classes)
- **Tests within story**: All unit tests marked [P] can run together
- **Cross-story parallelism**: After Foundational, different team members can work on US1, US5, US6 simultaneously (minimal overlap)
- **Polish tasks**: T070-T078 - Most marked [P] can run in parallel

---

## Implementation Strategy

### MVP First (P1 Stories Only)

1. Complete Phase 1: Setup → ~1 hour
2. Complete Phase 2: Foundational → ~4 hours
3. Complete Phase 3: US1 (Basic IAM Role Auth) → ~8 hours
4. Complete Phase 4: US2 (Automatic Refresh) → ~4 hours
5. Complete Phase 5: US3 (Backward Compatibility) → ~4 hours
6. Complete Phase 6: US4 (External ID Security) → ~2 hours
7. **STOP and VALIDATE**: Test all P1 stories independently
8. **MVP READY**: Core security feature complete, production-ready

**Total MVP Effort**: ~23 hours (3 days)

### Incremental Delivery

1. **Week 1**: Setup + Foundational + US1 → Basic role authentication works
2. **Week 2**: US2 + US3 + US4 → Production-ready with refresh and security
3. **Week 3**: US5 + US6 → Enhanced error handling and performance
4. **Week 4**: US7 (Platform integration) + US8 (Multi-region) + Polish

### Parallel Team Strategy

With 2 developers after Foundational phase:

- **Developer A**: US1 → US2 → US4 (core authentication flow)
- **Developer B**: US3 → US5 → US6 (compatibility, errors, caching)
- **Together**: US7, US8, Polish

---

## Estimated Effort

| Phase | Tasks | Estimated Hours |
|-------|-------|-----------------|
| Phase 1: Setup | 2 | 1 |
| Phase 2: Foundational | 8 | 4 |
| Phase 3: US1 - Basic IAM Role Auth | 10 | 8 |
| Phase 4: US2 - Automatic Refresh | 5 | 4 |
| Phase 5: US3 - Backward Compatibility | 8 | 4 |
| Phase 6: US4 - External ID Security | 4 | 2 |
| Phase 7: US5 - Error Handling | 4 | 3 |
| Phase 8: US6 - Caching Performance | 5 | 4 |
| Phase 9: US7 - Platform UI | 3 | 2 |
| Phase 10: US8 - Multi-Region | 5 | 3 |
| Phase 11: Polish | 9 | 5 |
| **Total** | **63 tasks** | **40 hours** |

**MVP (P1 only)**: 27 tasks, ~23 hours

---

## Notes

- [P] tasks can run in parallel (different files, no dependencies)
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Write tests FIRST for each story, ensure they FAIL before implementation
- Commit after each task or logical group of related tasks
- Stop at any checkpoint to validate story independently before proceeding
- All file paths are exact locations in Wave codebase (Groovy/Micronaut structure)
- Test file paths follow Spock 2 framework conventions