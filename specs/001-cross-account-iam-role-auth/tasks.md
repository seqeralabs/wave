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

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T011 [P] [US1] Create unit test `AwsEcrServiceSpec.groovy` test case "should detect role ARN pattern in username" in `src/test/groovy/io/seqera/wave/service/aws/AwsEcrServiceSpec.groovy`
- [ ] T012 [P] [US1] Create unit test case "should NOT detect static credentials as role ARN" in `AwsEcrServiceSpec.groovy`
- [ ] T013 [P] [US1] Create integration test `AwsEcrIamRoleIntegrationSpec.groovy` in `src/test/groovy/io/seqera/wave/service/aws/` for end-to-end role assumption

### Implementation for User Story 1

- [ ] T014 [US1] Implement `isRoleArn(String username)` method in `AwsEcrService` that matches pattern `^arn:aws:iam::\d{12}:role/.+`
- [ ] T015 [US1] Implement `generateSessionName(String registry)` method in `AwsEcrService` that creates session name format `wave-ecr-{registry-short}-{timestamp}`
- [ ] T016 [US1] Implement `assumeRoleAndAuthenticate(String roleArn, String externalId, String region)` method in `AwsEcrService`
  - Call STS `assumeRole()` with role ARN, external ID, session name, 1-hour duration
  - Extract temporary credentials (access key, secret key, session token, expiration)
  - Return `AwsCreds` object with session token populated
- [ ] T017 [US1] Modify `getLoginToken(String registry, String username, String password)` method in `AwsEcrService` to detect role ARN and route to `assumeRoleAndAuthenticate()`
- [ ] T018 [US1] Update ECR authentication logic to use `AwsSessionCredentials.create()` when session token is present
- [ ] T019 [US1] Add STS error handling for `AccessDeniedException`, `InvalidParameterException`, `RegionDisabledException` with specific error messages
- [ ] T020 [US1] Add logging for STS AssumeRole calls with context: role ARN, region, external ID presence, success/failure

**Checkpoint**: User Story 1 complete - Wave can authenticate using IAM roles with STS AssumeRole

---

## Phase 4: User Story 2 - Automatic Credential Refresh (Priority: P1)

**Goal**: Wave automatically refreshes temporary credentials before they expire to prevent authentication failures

**Independent Test**: Configure role-based auth, monitor credentials approaching expiration, verify proactive refresh at 5-minute buffer

### Tests for User Story 2

- [ ] T021 [P] [US2] Create unit test case "should detect credentials expiring within 5 minutes" in `AwsEcrCacheSpec.groovy`
- [ ] T022 [P] [US2] Create unit test case "should NOT detect credentials with more than 5 minutes remaining" in `AwsEcrCacheSpec.groovy`
- [ ] T023 [P] [US2] Create integration test "should automatically refresh credentials before expiration" in `AwsEcrIamRoleIntegrationSpec.groovy`

### Implementation for User Story 2

- [ ] T024 [US2] Update `AwsEcrCache` cache configuration to use `expireAfter()` with custom `Expiry` implementation
- [ ] T025 [US2] Implement cache TTL calculation that expires credentials 5 minutes before STS expiration (role-based) or ECR token expiration (static)
- [ ] T026 [US2] Modify `getLoginToken()` to check `isExpiring()` on cached credentials and invalidate if expiring soon
- [ ] T027 [US2] Add retry logic for expired credentials: detect `ExpiredTokenException` and retry with fresh credentials
- [ ] T028 [US2] Add logging for credential refresh events with context: role ARN, time until expiration, reason for refresh

**Checkpoint**: User Story 2 complete - Credentials refresh proactively, no authentication failures from expiration

---

## Phase 5: User Story 3 - Backward Compatibility with Static Credentials (Priority: P1)

**Goal**: Existing static credential configurations continue to work without any changes

**Independent Test**: Deploy new code with static credentials configured, pull images, verify all operations work identically

### Tests for User Story 3

- [ ] T029 [P] [US3] Create integration test "should authenticate with static credentials using existing flow" in `AwsEcrIamRoleIntegrationSpec.groovy`
- [ ] T030 [P] [US3] Create integration test "should handle mixed environment with both static and role-based credentials" in `AwsEcrIamRoleIntegrationSpec.groovy`
- [ ] T031 [P] [US3] Create backward compatibility test suite that verifies existing `AwsEcrServiceSpec` tests still pass

### Implementation for User Story 3

- [ ] T032 [US3] Verify `AwsCreds.stableHash()` handles null session token correctly with `${sessionToken ?: ''}` pattern
- [ ] T033 [US3] Verify `CachedEcrCredentials.isExpiring()` falls back to `tokenExpiration` when `stsExpiration` is null
- [ ] T034 [US3] Add unit test case "should create AwsBasicCredentials when session token is null" in `AwsEcrServiceSpec.groovy`
- [ ] T035 [US3] Add unit test case "should create AwsSessionCredentials when session token is present" in `AwsEcrServiceSpec.groovy`
- [ ] T036 [US3] Run full existing test suite and verify zero regressions

**Checkpoint**: User Story 3 complete - Both authentication methods work correctly in same deployment

---

## Phase 6: User Story 4 - External ID Security (Priority: P1)

**Goal**: Wave correctly uses external ID in AssumeRole calls to prevent confused deputy attacks

**Independent Test**: Create multiple credentials with unique external IDs, verify Wave includes correct external ID in each STS call

### Tests for User Story 4

- [ ] T037 [P] [US4] Create unit test "should include external ID in AssumeRole request" in `AwsEcrServiceSpec.groovy`
- [ ] T038 [P] [US4] Create integration test "should fail role assumption with incorrect external ID" in `AwsEcrIamRoleIntegrationSpec.groovy`
- [ ] T039 [P] [US4] Create integration test "should succeed role assumption with correct external ID" in `AwsEcrIamRoleIntegrationSpec.groovy`

### Implementation for User Story 4

- [ ] T040 [US4] Verify `assumeRoleAndAuthenticate()` includes external ID parameter in `AssumeRoleRequest.builder().externalId()`
- [ ] T041 [US4] Add validation for external ID format (2-1224 characters, pattern `[\w+=,.@:\/-]*`)
- [ ] T042 [US4] Add error handling for external ID mismatch errors from STS
- [ ] T043 [US4] Add logging to indicate external ID presence in AssumeRole calls (log "with external ID" but NOT the actual ID value)

**Checkpoint**: User Story 4 complete - External ID security working correctly

---

## Phase 7: User Story 5 - Comprehensive Error Handling (Priority: P2)

**Goal**: Wave provides clear, actionable error messages for IAM role authentication failures

**Independent Test**: Intentionally misconfigure role ARN, trust policy, external ID, verify Wave returns specific error messages

### Tests for User Story 5

- [ ] T044 [P] [US5] Create unit test "should return specific error for missing trust policy (AccessDenied)" in `AwsEcrServiceSpec.groovy`
- [ ] T045 [P] [US5] Create unit test "should return specific error for invalid external ID format" in `AwsEcrServiceSpec.groovy`
- [ ] T046 [P] [US5] Create unit test "should return specific error for region with STS disabled" in `AwsEcrServiceSpec.groovy`

### Implementation for User Story 5

- [ ] T047 [US5] Create comprehensive STS error mapping in `assumeRoleAndAuthenticate()` method:
  - `AccessDeniedException` → "Wave's service role does not have permission to assume the specified role. Verify the trust policy in your IAM role."
  - `InvalidParameterException` (external ID) → "Invalid external ID format. External ID must be 2-1224 characters and match pattern [\w+=,.@:\/-]*"
  - `RegionDisabledException` → "STS is not enabled in the specified region. Enable STS endpoints for this region in AWS."
- [ ] T048 [US5] Add structured logging with sufficient context for troubleshooting: role ARN, region, error code, error message
- [ ] T049 [US5] Update error messages to include remediation guidance and links to AWS documentation
- [ ] T050 [US5] Add error logging for credential expiration during operations: "Temporary credentials have expired. Retrying with fresh credentials."

**Checkpoint**: User Story 5 complete - Clear error messages guide administrators to resolve issues quickly

---

## Phase 8: User Story 6 - Credential Caching for Performance (Priority: P2)

**Goal**: Wave efficiently caches temporary credentials to minimize STS API calls and optimize performance

**Independent Test**: Make multiple auth requests with same role, monitor STS calls, verify only one AssumeRole per hour

### Tests for User Story 6

- [ ] T051 [P] [US6] Create unit test "should cache credentials with unique key including session token" in `AwsEcrCacheSpec.groovy`
- [ ] T052 [P] [US6] Create integration test "should return cached credentials for duplicate requests" in `AwsEcrIamRoleIntegrationSpec.groovy`
- [ ] T053 [P] [US6] Create performance test "should achieve >95% cache hit rate for typical workload" in `AwsEcrIamRoleIntegrationSpec.groovy`

### Implementation for User Story 6

- [ ] T054 [US6] Verify cache key includes all required fields: access key, secret key, session token, region, ECR public flag
- [ ] T055 [US6] Implement Caffeine `get(key, mappingFunction)` pattern to prevent concurrent STS calls for same uncached role
- [ ] T056 [US6] Add cache metrics using Micrometer: `wave.ecr.auth.cache.hit_rate`, `wave.ecr.auth.cache.size`, `wave.ecr.auth.cache.evictions`
- [ ] T057 [US6] Configure cache maximum size (e.g., 10,000 entries) to prevent unbounded memory growth
- [ ] T058 [US6] Add cache statistics logging: hit rate, miss rate, eviction count at regular intervals

**Checkpoint**: User Story 6 complete - Credential caching optimized for performance and cost

---

## Phase 9: User Story 7 - Platform UI Integration (Priority: P2)

**Goal**: Platform provides intuitive UI for configuring IAM role-based authentication with auto-generated external ID

**Independent Test**: Access Platform credential config UI, enter role ARN, verify external ID auto-generation and copy functionality

**NOTE**: This phase is primarily Platform (nf-tower-cloud) work, but Wave may need minor changes for integration

### Wave Tasks for User Story 7

- [ ] T059 [US7] Verify Wave correctly receives external ID from Platform via credential payload
- [ ] T060 [US7] Add validation to ensure external ID is never null when role ARN is provided
- [ ] T061 [US7] Add logging to help debug Platform-Wave credential passing

**Checkpoint**: User Story 7 complete - Platform UI integration working smoothly

---

## Phase 10: User Story 8 - Multi-Region Support (Priority: P3)

**Goal**: Wave handles IAM role authentication across different AWS regions correctly

**Independent Test**: Configure role-based auth for ECR in different regions (us-east-1, eu-west-1, ap-southeast-1), verify all work

### Tests for User Story 8

- [ ] T062 [P] [US8] Create integration test "should authenticate to ECR in us-east-1" in `AwsEcrMultiRegionSpec.groovy`
- [ ] T063 [P] [US8] Create integration test "should authenticate to ECR in eu-west-1" in `AwsEcrMultiRegionSpec.groovy`
- [ ] T064 [P] [US8] Create integration test "should authenticate to ECR in ap-southeast-1" in `AwsEcrMultiRegionSpec.groovy`

### Implementation for User Story 8

- [ ] T065 [US8] Implement `extractRegion(String registry)` method that parses region from ECR registry URL pattern
- [ ] T066 [US8] Implement `createRegionalStsClient(String region)` method for regional STS endpoints (optimization)
- [ ] T067 [US8] Update `assumeRoleAndAuthenticate()` to use regional STS endpoint based on ECR registry region
- [ ] T068 [US8] Add fallback to global STS endpoint if regional parsing fails
- [ ] T069 [US8] Add logging for regional STS endpoint usage

**Checkpoint**: User Story 8 complete - Multi-region support working globally

---

## Phase 11: Polish & Cross-Cutting Concerns

**Purpose**: Metrics, monitoring, documentation, and quality improvements

- [ ] T070 [P] Add Micrometer metrics for STS operations:
  - `wave.sts.assume_role.calls` (counter with tags: success/failure, region)
  - `wave.sts.assume_role.duration` (timer for STS call latency)
  - `wave.ecr.auth.method` (counter with tags: static/role)
  - `wave.ecr.credentials.expiring_soon` (gauge for credentials <5 min to expiration)
- [ ] T071 [P] Update `CLAUDE.md` with new STS authentication flow and architecture decisions
- [ ] T072 [P] Add JavaDoc comments to all new public methods in `AwsEcrService`
- [ ] T073 [P] Add @Retryable annotation to `assumeRoleAndAuthenticate()` for automatic retry on transient STS errors (3 attempts, exponential backoff)
- [ ] T074 [P] Implement custom `toString()` methods for `AwsCreds` and `CachedEcrCredentials` to prevent credential logging
- [ ] T075 Update changelog.txt with feature description and version bump
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