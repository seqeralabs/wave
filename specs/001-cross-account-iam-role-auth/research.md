# Research: Cross-Account IAM Role Authentication

## Technology Choices

### AWS SDK for Java v2

**Selected**: AWS SDK for Java 2.x (`software.amazon.awssdk:sts`)

**Rationale**:
- Wave already uses AWS SDK v2 for ECR operations (see existing usage in `AwsEcrService`)
- Provides native STS client (`StsClient`) with AssumeRole support
- Better performance and smaller footprint than SDK v1
- Active maintenance and security updates

**Alternatives Considered**:
- AWS SDK v1: Deprecated, blocking I/O, incompatible with reactive patterns
- Third-party libraries: Unnecessary abstraction, additional maintenance burden

### Credential Provider Strategy

**Selected**: Hybrid approach - `StaticCredentialsProvider` for basic credentials, `AwsSessionCredentials` for role-based

**Rationale**:
- `AwsBasicCredentials`: Existing pattern for static access key + secret key
- `AwsSessionCredentials`: Native support for temporary credentials with session token
- Both implement `AwsCredentials` interface - polymorphic compatibility
- No need for custom credential providers

**Implementation** (`AwsEcrService.groovy:390`):
```groovy
private static StaticCredentialsProvider credentialsProvider(String accessKey, String secretKey, String sessionToken) {
    sessionToken != null
            ? StaticCredentialsProvider.create(AwsSessionCredentials.create(accessKey, secretKey, sessionToken))
            : StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
}
```

### Caching Strategy

**Selected**: `AbstractTieredCache` with dynamic per-entry TTL via `Pair<V, Duration>`

**Rationale**:
- Wave uses `AbstractTieredCache` (L1 in-memory + L2 Redis) for credential caching
- Two caches: `AwsEcrCache` for ECR auth tokens, `AwsRoleCache` for jump role STS credentials
- TTL set at write time using `computeCacheTtl()` — no `isExpiring()` polling
- `Pair<AwsEcrAuthToken, Duration>` enables dynamic per-entry TTL based on STS credential expiration
- Maintains consistency with existing tiered cache patterns in Wave

**Cache Value**:
```groovy
// ECR auth token (simple wrapper)
class AwsEcrAuthToken implements MoshiSerializable {
    String value  // Base64-decoded username:password from ECR
}

// Jump role STS credentials (serializable for L2)
class AwsStsCredentials implements MoshiSerializable {
    String accessKeyId
    String secretAccessKey
    String sessionToken
    long expirationEpochMilli  // long for Moshi compatibility (not Instant)
}
```

### Session Token Handling

**Selected**: Include session token in `AwsCreds` cache key class with conditional hash inclusion

**Rationale**:
- Session token is required for all API calls with temporary credentials
- Must be part of cache key hash to distinguish different credential sessions
- Only included in hash when non-null, preserving backward-compatible hashes for static credentials

**AwsCreds** (`AwsEcrService.groovy:96`):
```groovy
@Canonical
private static class AwsCreds implements TieredKey {
    String accessKey      // static credentials flow
    String secretKey      // static credentials flow
    String roleArn        // role-based auth flow
    String externalId     // role-based auth flow
    String sessionToken   // temporary credentials from STS
    String region
    boolean ecrPublic

    @Override
    String stableHash() {
        if (roleArn) {
            return RegHelper.sipHash(roleArn, externalId, region, ecrPublic)
        }
        final args = [accessKey, secretKey, region, ecrPublic] as List<Object>
        if (sessionToken) args.add(sessionToken)
        return RegHelper.sipHash(args.toArray())
    }

    static AwsCreds ofRole(String roleArn, String externalId, String region, boolean ecrPublic) { /* ... */ }
    static AwsCreds ofKeys(String accessKey, String secretKey, String sessionToken, String region, boolean ecrPublic) { /* ... */ }
}
```

## Existing Codebase Patterns

### Service Layer Architecture

**Pattern**: Wave follows service-oriented architecture with clear separation of concerns

**Relevant Services**:
- `AwsEcrService`: Handles ECR authentication and registry operations
  - Located: `src/main/groovy/io/seqera/wave/service/aws/AwsEcrService.groovy`
  - Key method: `getLoginToken(String accessKey, String secretKey, String region, boolean isPublic)`
  - Responsibilities: ECR authentication, STS role assumption, jump role chaining, credential caching

- `AwsEcrCache`: Tiered cache for ECR auth tokens
  - Located: `src/main/groovy/io/seqera/wave/service/aws/cache/AwsEcrCache.groovy`
  - Extends `AbstractTieredCache<TieredKey, AwsEcrAuthToken>` (L1 in-memory + L2 Redis)
  - Config: `wave.aws.ecr.cache.{duration:3h, max-size:10000}`

- `AwsRoleCache`: Tiered cache for jump role STS credentials
  - Located: `src/main/groovy/io/seqera/wave/service/aws/cache/AwsRoleCache.groovy`
  - Extends `AbstractTieredCache<String, AwsStsCredentials>` (keyed by region)
  - Config: `wave.aws.jump-role-cache.{duration:45m, max-size:100}`

**Integration Point**: `AwsEcrService.getLoginToken()`:
1. Detect role ARN pattern in `accessKey` parameter via `isRoleArn()`
2. If role ARN detected: Assume role via STS (with optional jump role chaining), cache ECR token with dynamic TTL
3. If static credentials: Use existing flow (backward compatible)

### Micronaut Dependency Injection

**Pattern**: Field-based injection with `@Inject` and `@Value` annotations

**Implementation**:
```groovy
@Singleton
class AwsEcrService {
    @Inject private AwsEcrCache cache           // ECR auth token cache
    @Inject private AwsRoleCache jumpRoleCache   // Jump role STS credential cache
    @Inject private StsClientConfig stsConfig    // Retry configuration

    @Nullable @Value('${wave.aws.jump-role-arn}')
    private String jumpRoleArn                   // Jump role ARN (optional)

    @Nullable @Value('${wave.aws.jump-external-id}')
    private String jumpExternalId                // Jump role external ID (optional)
}
```

**STS Client**: Per-region static factory methods (no singleton bean):
```groovy
// Default credentials (Wave's own IAM role/env credentials)
protected static StsClient stsClient(String region) {
    StsClient.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.builder().build())
        .build()
}

// Session credentials (from a prior role assumption, e.g., jump role)
protected static StsClient stsClient(String region, Credentials credentials) {
    StsClient.builder()
        .region(Region.of(region))
        .credentialsProvider(credentialsProvider(credentials.accessKeyId(), credentials.secretAccessKey(), credentials.sessionToken()))
        .build()
}
```

**Rationale**: Per-region clients ensure STS calls use the correct regional endpoint. Clients are created per-call and closed via `withCloseable`. Follows the same pattern as existing `ecrClient()` and `ecrPublicClient()` methods.

### Error Handling Patterns

**Pattern**: Wave uses `AwsEcrAuthException` (extends `WaveException`) for all STS/ECR auth errors

**Implementation** (`AwsEcrService.groovy:357`):
```groovy
protected static Exception mapStsException(StsException e) {
    switch (e.awsErrorDetails()?.errorCode()) {
        case 'AccessDenied':           → AwsEcrAuthException (trust policy guidance)
        case 'InvalidParameterValue':  → AwsEcrAuthException (ARN format guidance)
        case 'RegionDisabledException': → AwsEcrAuthException (STS endpoint guidance)
        case 'ExpiredTokenException':   → AwsEcrAuthException (retry guidance)
        default:                        → AwsEcrAuthException (generic STS failure)
    }
}
```

### Async/Reactive Patterns

**Decision**: Synchronous STS calls inside `cache.getOrCompute()`

**Justification**: The cache hit path (>95% of requests per NFR-002) involves zero I/O — only the cache miss path triggers a synchronous STS call, bounded by NFR-003 (<2s). The STS call runs inside the tiered cache's compute function, not directly from an HTTP request handler. If latency profiling shows contention under load, wrap in `Mono.fromCallable()` on the blocking executor as a follow-up.

### Testing Framework

**Pattern**: Spock 2 framework with BDD-style specifications

**Test Structure** (`AwsEcrServiceTest.groovy`):
- Unit tests for `isRoleArn()`, `extractAccountId()`, `computeCacheTtl()`, `stableHash()`
- Mocked STS tests for role assumption, jump role chaining, error handling
- Backward compatibility tests for static credentials
- `AwsStsCredentials` round-trip serialization tests

## Architecture Decisions

### Decision 1: Role ARN Detection via Pattern Matching

**Decision**: Detect role ARN by pattern matching in the `accessKey` parameter

**Pattern**: `^arn:aws(-cn|-us-gov)?:iam::\d{12}:role/[\w+=,.@\/-]+$`

**Rationale**:
- Platform passes `assumeRoleArn` → `userName` and `externalId` → `password` via `ContainerRegistryKeys.fromJson()`
- ARN format is unambiguous across all AWS partitions (standard, China, GovCloud)
- No API changes required - backward compatible
- Simple regex pattern matching

**Alternative Rejected**: Add new boolean flag `useRoleBasedAuth`
- Requires API changes in Platform and Wave
- Extra configuration burden on users
- Pattern detection is simpler and more intuitive

### Decision 2: External ID in Platform, Not Wave

**Decision**: Platform generates and stores external ID, passes it to Wave via existing credential fields

**Rationale**:
- External ID is workspace-specific, not per-request
- Platform already manages credential metadata
- Wave remains stateless - external ID comes with each auth request
- Clear separation of concerns: Platform = configuration, Wave = execution

**Implementation**: Platform's `ContainerRegistryKeys.fromJson()` maps `assumeRoleArn` → `userName`, `externalId` → `password`. Wave detects the role ARN pattern in `userName` to route to role-based authentication.

### Decision 3: 1-Hour Session Duration

**Decision**: Request 1-hour (3600 second) session duration from STS

**Rationale**:
- Balance between security (shorter = better) and performance (longer = fewer STS calls)
- 1 hour aligns with typical container pull operation duration
- AWS allows 15 min - 12 hours; 1 hour is conservative middle ground
- With 5-minute refresh buffer via cache TTL, credentials refresh every ~55 minutes

**Alternative Rejected**: 12-hour sessions
- Longer sessions increase blast radius if credentials compromised
- Minimal performance benefit (cache hit rate already >95% with 1-hour)

### Decision 4: TTL-Based Cache Eviction (Not `isExpiring()` Polling)

**Decision**: Set cache TTL at write time via `computeCacheTtl()` instead of polling for expiring credentials

**Rationale**:
- `AbstractTieredCache` supports `Pair<V, Duration>` for dynamic per-entry TTL
- TTL = `min(STS_expiration - 5min_buffer, max_cache_duration)` with 1-minute floor
- When TTL expires, cache evicts automatically — next request triggers fresh `assumeRole()` + ECR token load
- No background threads or polling needed
- 5-minute buffer prevents authentication failures during active operations

**Implementation** (`AwsEcrService.groovy:524`):
```groovy
static final private Duration REFRESH_BUFFER = Duration.ofMinutes(5)
static final private Duration MIN_CACHE_TTL = Duration.ofMinutes(1)

protected static Duration computeCacheTtl(Instant expiration, Duration maxDuration) {
    if (expiration == null) return maxDuration
    final timeUntilExpiry = Duration.between(Instant.now(), expiration)
    final bufferedTtl = timeUntilExpiry.minus(REFRESH_BUFFER)
    if (bufferedTtl.compareTo(MIN_CACHE_TTL) < 0) return MIN_CACHE_TTL
    return bufferedTtl.compareTo(maxDuration) < 0 ? bufferedTtl : maxDuration
}
```

### Decision 5: Per-Region STS Clients

**Decision**: Create per-region STS clients via static factory methods, not a singleton

**Rationale**:
- Each region needs its own STS endpoint for correct regional routing
- Factory method pattern follows existing `ecrClient()` / `ecrPublicClient()` patterns
- Clients closed via `withCloseable` after use — no connection pool management needed
- `DefaultCredentialsProvider` handles environment variables, EC2 instance profile, ECS task role

### Decision 6: Backward Compatibility via Pattern Detection

**Decision**: Automatically detect authentication method based on username format - no configuration flag

**Rationale**:
- Zero migration effort for existing customers
- No risk of breaking changes during deployment
- Role ARN pattern is unambiguous - no false positives across all AWS partitions
- Gradual migration: customers can switch when ready

### Decision 7: Session Name Format

**Decision**: Use format `wave-ecr-{accountId}-{timestamp}` for target role sessions, `wave-jump-{accountId}-{timestamp}` for jump role sessions

**Rationale**:
- Session name appears in CloudTrail logs — helps customers audit Wave access
- Account ID extracted from role ARN via `extractAccountId()` for traceability
- Timestamp enables correlation with Wave application logs
- Returns `unknown` if ARN cannot be parsed
- Max 64 characters — compact format stays under limit

**Implementation** (`AwsEcrService.groovy:277`):
```groovy
protected static AssumeRoleRequest buildAssumeRoleRequest(String roleArn, String externalId, String sessionPrefix) {
    final builder = AssumeRoleRequest.builder()
            .roleArn(roleArn)
            .roleSessionName("${sessionPrefix}-${extractAccountId(roleArn)}-${System.currentTimeMillis()}")
            .durationSeconds(SESSION_DURATION_SECONDS)
    if (externalId) { builder.externalId(externalId) }
    return builder.build()
}
```

### Decision 8: Jump Role Chaining

**Decision**: Support optional two-hop role assumption via intermediate jump role

**Rationale**:
- Some organizations don't allow direct cross-account trust from Wave's identity
- Jump role acts as a trusted intermediary within Wave's AWS account or org
- Configurable via `wave.aws.jump-role-arn` and `wave.aws.jump-external-id`
- Jump role credentials cached in `AwsRoleCache` (keyed by region) to avoid redundant STS calls
- When not configured, Wave assumes target role directly (no jump role overhead)

**Implementation**:
- `assumeRole()` checks `jumpRoleArn` → chains through `assumeJumpRole()` if set
- `assumeJumpRole()` uses `AwsRoleCache.getOrCompute()` with dynamic TTL
- `AwsStsCredentials` (MoshiSerializable) wraps STS credentials for cache serialization
- ExpiredTokenException retry: if jump role credentials expired (race with cache TTL), retries once via `doAssumeJumpRole()` (cache bypass)

### Decision 9: Programmatic Retry with Retryable.of()

**Decision**: Use `Retryable.of(stsConfig)` for STS retry instead of annotation-based `@Retryable`

**Rationale**:
- `StsClientConfig` implements `Retryable.Config` — follows existing `HttpClientConfig` pattern
- Retry config injected via `@Value`: `wave.aws.sts.retry.{delay:1s, maxDelay:10s, attempts:3, multiplier:2.0, jitter:0.25}`
- `isRetryableStsError()` checks for 5xx server errors and `Throttling` error code
- Programmatic retry allows custom retry conditions and per-method control

## Performance Considerations

### STS API Latency

**Expected Latency**: 200-500ms for AssumeRole calls (AWS SDK overhead + network + STS processing)

**Impact**: Minimal due to caching
- First request per role: +200-500ms (one-time cost)
- Subsequent requests: Cache hit, no STS call
- Cache hit rate: Expected >95% (credentials valid for 1 hour, refresh at ~55 minutes)

### Cache Memory Overhead

**Per-Credential Entry**: ~2.6 KB (role-based) / ~1.6 KB (static)

**Scale Estimate**:
- 1000 concurrent customers = ~2.6 MB memory
- 10,000 concurrent customers = ~26 MB memory
- Negligible compared to Wave's typical heap (2 GB)

### Concurrent AssumeRole Requests

**Concern**: Multiple requests for same uncached role arriving simultaneously

**Mitigation**: `AbstractTieredCache.getOrCompute()` ensures only one thread performs the load
- Other threads block waiting for the result
- No duplicate STS calls for the same key

## Security Considerations

### Confused Deputy Attack Prevention

**Threat**: Attacker tricks Wave into accessing their resources using Wave's credentials

**Mitigation**: External ID requirement
- Each workspace has unique UUID external ID (generated by Platform)
- Customer's IAM role trust policy MUST specify this exact external ID
- Without correct external ID, STS denies AssumeRole request

### Credential Exposure Risk

**Concern**: Temporary credentials logged or exposed

**Mitigation**:
- Never log `secretAccessKey` or `sessionToken` in plaintext
- `AwsCreds` is a private inner class — `@Canonical` generates toString but credentials are only logged via explicit log statements which redact sensitive fields
- `AwsStsCredentials` uses `@ToString(excludes = ['secretAccessKey', 'sessionToken'])` to prevent accidental logging
- Log external ID presence (`'provided'`/`'none'`), never the value

## Dependencies Required

### New Dependencies

**STS SDK** (already added):
```gradle
implementation 'software.amazon.awssdk:sts:2.20.+'
```

**Notes**:
- AWS SDK BOM manages version consistency across ECR and STS clients
- STS SDK is small (~2 MB) and has minimal transitive dependencies
- Compatible with existing AWS SDK v2 usage in Wave

### Existing Dependencies (No Changes)

- AWS SDK ECR: Already present for ECR operations
- AbstractTieredCache: Already used for tiered caching (L1 in-memory + L2 Redis)
- Micrometer: Already used for metrics
- Moshi: Already used for cache serialization

## Platform Changes Required

### 1. AwsSecurityKeys Domain Class Extension

**File**: `tower-core/src/main/groovy/io/seqera/tower/domain/aws/AwsSecurityKeys.groovy:74`

**Changes**:
- Add `assumeRoleArn` field (role ARN for cross-account access)
- Add `externalId` field (auto-generated UUID)
- `ContainerRegistryKeys.fromJson()` maps `assumeRoleArn` → `userName`, `externalId` → `password`

### 2. Database Migration

Add `external_id` column to `aws_security_keys` table

### 3. Validator Extension

Validate role ARN format and external ID format before saving

## Rollout Strategy

### Phase 1: Wave Changes Only (This Repository)

Implement core STS functionality in Wave:
- STS client factory methods and role ARN detection
- Jump role chaining with AwsRoleCache
- Credential caching with dynamic TTL
- Comprehensive Spock 2 tests

**Deliverable**: Wave can authenticate using role ARN + external ID (passed as parameters)

### Phase 2: Platform Changes (nf-tower-cloud)

Extend Platform to support external ID:
- Add `externalId` field to AwsSecurityKeys
- Create database migration
- Update validators and UI

### Phase 3: Integration Testing

End-to-end testing across Platform + Wave

### Phase 4: Production Rollout

Gradual deployment with monitoring

## References

- [AWS STS AssumeRole API](https://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRole.html)
- [AWS SDK for Java 2.x Developer Guide](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/)
- [Confused Deputy Problem](https://docs.aws.amazon.com/IAM/latest/UserGuide/confused-deputy.html)
- [AWS IAM External ID](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_create_for-user_externalid.html)
- Wave CLAUDE.md: Project conventions and architecture patterns
- Wave Constitution: Service-oriented architecture principles