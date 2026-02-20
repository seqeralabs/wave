# Research: Cross-Account IAM Role Authentication

## Technology Choices

### AWS SDK for Java v2

**Selected**: AWS SDK for Java 2.x (`software.amazon.awssdk:sts`)

**Rationale**:
- Wave already uses AWS SDK v2 for ECR operations (see existing usage in `AwsEcrService`)
- Provides native STS client (`StsClient`) with AssumeRole support
- Supports non-blocking async operations compatible with Micronaut Reactor
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

**Implementation Pattern**:
```groovy
AwsCredentials createCredentials(String accessKey, String secretKey, String sessionToken) {
    return sessionToken
        ? AwsSessionCredentials.create(accessKey, secretKey, sessionToken)
        : AwsBasicCredentials.create(accessKey, secretKey)
}
```

### Caching Strategy

**Selected**: Extend existing `AwsEcrCache` with expiration tracking

**Rationale**:
- Wave already has `AwsEcrCache` using Caffeine cache library
- Add `Instant expiration` field to cached credential entries
- Implement `isExpiring()` method with 5-minute buffer logic
- Maintains consistency with existing caching patterns

**Cache Entry Structure**:
```groovy
@Canonical
class CachedEcrCredentials {
    String accessKeyId
    String secretAccessKey
    String sessionToken       // NEW
    Instant expiration        // NEW
    String authToken          // ECR auth token
    Instant tokenExpiration
}
```

### Session Token Handling

**Selected**: Include session token in `AwsCreds` class and cache key calculation

**Rationale**:
- Session token is required for all API calls with temporary credentials
- Must be part of cache key to distinguish different credential sessions
- Update `stableHash()` method to include session token in hash calculation

**Modified AwsCreds**:
```groovy
@Canonical
class AwsCreds {
    String accessKey
    String secretKey
    String sessionToken    // NEW - null for static credentials
    String region
    boolean ecrPublic

    String stableHash() {
        Hashing.sha256()
            .hashString("$accessKey:$secretKey:${sessionToken ?: ''}:$region:$ecrPublic", Charsets.UTF_8)
            .toString()
    }
}
```

## Existing Codebase Patterns

### Service Layer Architecture

**Pattern**: Wave follows service-oriented architecture with clear separation of concerns

**Relevant Services**:
- `AwsEcrService`: Handles ECR authentication and registry operations
  - Located: `src/main/groovy/io/seqera/wave/service/aws/AwsEcrService.groovy`
  - Key method: `getLoginToken(String registry, String username, String password)`
  - Responsibilities: ECR authentication, token management, credential validation

- `AwsEcrCache`: Manages credential caching
  - Located: `src/main/groovy/io/seqera/wave/service/aws/cache/AwsEcrCache.groovy`
  - Uses Caffeine cache with expiration policies
  - Key structure: `AwsCreds` → `CachedEcrCredentials`

**Integration Point**: Modify `AwsEcrService.getLoginToken()` to:
1. Detect role ARN pattern in `username` parameter
2. If role ARN detected: Call STS AssumeRole, cache temporary credentials
3. If static credentials: Use existing flow (backward compatible)

### Micronaut Dependency Injection

**Pattern**: Constructor-based dependency injection with `@Singleton` services

**Implementation**:
```groovy
@Singleton
class AwsEcrService {

    @Inject StsClient stsClient           // NEW - inject STS client
    @Inject AwsEcrCache ecrCache          // Existing
    @Inject AwsEcrConfig ecrConfig        // Existing

    // ...
}
```

**STS Client Configuration**:
Create factory bean in `AwsEcrConfig` or separate config class:
```groovy
@Factory
class AwsStsConfig {

    @Singleton
    StsClient stsClient() {
        StsClient.builder()
            .region(Region.AWS_GLOBAL)  // Or derive from ECR registry region
            .build()
    }
}
```

### Error Handling Patterns

**Pattern**: Wave uses custom exceptions extending `RuntimeException`

**Existing**:
- `BadRequestException`: Client errors (400)
- `UnauthorizedException`: Auth errors (401)
- `NotFoundException`: Resource not found (404)

**New Exception Handling**:
```groovy
try {
    AssumeRoleResponse response = stsClient.assumeRole(request)
} catch (StsException e) {
    throw new UnauthorizedException(
        "Failed to assume IAM role: ${e.awsErrorDetails().errorMessage()}",
        e
    )
}
```

### Async/Reactive Patterns

**Pattern**: Wave uses Micronaut Reactor with `Mono` and `Flux` for I/O operations

**Note**: STS AssumeRole is typically fast (<500ms), but should still be non-blocking:

```groovy
Mono<AssumeRoleResponse> assumeRoleAsync(AssumeRoleRequest request) {
    return Mono.fromFuture(stsAsyncClient.assumeRole(request))
}
```

**Decision**: Start with synchronous STS calls (simpler), optimize to async if latency becomes issue

### Testing Framework

**Pattern**: Spock 2 framework with BDD-style specifications

**Existing Test Structure**:
```groovy
class AwsEcrServiceSpec extends Specification {

    def "should authenticate with static credentials"() {
        given:
        def service = new AwsEcrService(...)

        when:
        def token = service.getLoginToken(registry, accessKey, secretKey)

        then:
        token.username == 'AWS'
        token.password != null
    }
}
```

**New Tests Needed**:
- Unit tests: Role ARN detection, STS integration, cache expiration logic
- Integration tests: End-to-end authentication with real STS (using test IAM roles)
- Backward compatibility tests: Verify static credentials still work

## Architecture Decisions

### Decision 1: Role ARN Detection via Username Field

**Decision**: Detect role ARN by pattern matching in the `username` parameter

**Rationale**:
- Platform already uses `username` field for both access keys and role ARNs
- ARN format is unambiguous: `arn:aws:iam::123456789012:role/RoleName`
- No API changes required - backward compatible
- Simple regex pattern: `^arn:aws:iam::\d{12}:role/.+`

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

**Implementation**: Platform extends `AwsSecurityKeys` with `externalId` field, includes in credential payload to Wave

### Decision 3: 1-Hour Session Duration

**Decision**: Request 1-hour (3600 second) session duration from STS

**Rationale**:
- Balance between security (shorter = better) and performance (longer = fewer STS calls)
- 1 hour aligns with typical container pull operation duration
- AWS allows 15 min - 12 hours; 1 hour is conservative middle ground
- With 5-minute refresh buffer, credentials refresh every ~55 minutes

**Alternative Rejected**: 12-hour sessions
- Longer sessions increase blast radius if credentials compromised
- Minimal performance benefit (cache hit rate already >95% with 1-hour)

### Decision 4: Proactive Credential Refresh (5-Minute Buffer)

**Decision**: Refresh credentials when they have <5 minutes remaining, not when expired

**Rationale**:
- Prevents authentication failures during active operations
- 5 minutes provides buffer for STS API latency and retries
- Small performance cost (slightly more STS calls) for much better reliability
- Aligns with AWS best practices for credential rotation

**Implementation**:
```groovy
boolean isExpiring(Instant expiration) {
    return expiration != null &&
        Instant.now().plus(Duration.ofMinutes(5)).isAfter(expiration)
}
```

### Decision 5: Regional STS Endpoints (Future Optimization)

**Decision**: Start with global STS endpoint, optimize to regional endpoints later if needed

**Rationale**:
- Global endpoint (`sts.amazonaws.com`) works for all regions
- Simplifies initial implementation
- Regional optimization can be added later without breaking changes
- Most Wave deployments are in us-east-1 (where global endpoint is located)

**Future Optimization**:
```groovy
String extractRegion(String registry) {
    // Example: 123456789012.dkr.ecr.us-west-2.amazonaws.com → us-west-2
    def matcher = registry =~ /\.ecr\.([a-z0-9-]+)\.amazonaws\.com/
    return matcher ? matcher[0][1] : 'us-east-1'
}

StsClient createRegionalStsClient(String region) {
    StsClient.builder().region(Region.of(region)).build()
}
```

### Decision 6: Backward Compatibility via Pattern Detection

**Decision**: Automatically detect authentication method based on username format - no configuration flag

**Rationale**:
- Zero migration effort for existing customers
- No risk of breaking changes during deployment
- Role ARN pattern is unambiguous - no false positives
- Gradual migration: customers can switch when ready

**Detection Logic**:
```groovy
boolean isRoleArn(String username) {
    return username?.matches(/^arn:aws:iam::\d{12}:role\/.+/)
}

def authenticate(String registry, String username, String password) {
    if (isRoleArn(username)) {
        return authenticateWithRole(username, password)  // password = externalId
    } else {
        return authenticateWithStaticCredentials(username, password)
    }
}
```

### Decision 7: Session Name Format

**Decision**: Use format `wave-ecr-{registry}-{timestamp}` for role session names

**Rationale**:
- Session name appears in CloudTrail logs - helps customers audit Wave access
- Including registry helps identify which customer registry was accessed
- Timestamp enables correlation with Wave application logs
- Max 64 characters - keep registry part short to stay under limit

**Implementation**:
```groovy
String generateSessionName(String registry) {
    def registryShort = registry.replaceAll(/[^a-zA-Z0-9]/, '').take(30)
    def timestamp = System.currentTimeMillis()
    return "wave-ecr-${registryShort}-${timestamp}"
}
```

## Unknowns & Investigation Needed

### 1. Platform Integration Points

**Question**: How does Platform pass credentials to Wave?

**Status**: ✅ KNOWN - Platform passes credentials via HTTP headers or request body when calling Wave APIs

**Action**: Review Platform's credential passing mechanism to ensure external ID can be included

### 2. STS Client Configuration

**Question**: Should STS client be singleton or request-scoped?

**Status**: ✅ DECIDED - Singleton with connection pooling

**Rationale**: STS calls are infrequent (cached for 1 hour), singleton reduces overhead

### 3. Cache Concurrency

**Question**: How to handle concurrent requests for same uncached role?

**Status**: ✅ DECIDED - Use Caffeine's `get(key, mappingFunction)` with atomic load

**Implementation**:
```groovy
CachedEcrCredentials getOrLoadCredentials(AwsCreds creds) {
    return cache.get(creds.stableHash()) { key ->
        // Only one thread executes this for the same key
        return assumeRoleAndCacheCredentials(creds)
    }
}
```

### 4. Error Recovery Strategy

**Question**: What to do when STS returns 5xx errors?

**Status**: ✅ DECIDED - Retry with exponential backoff (3 attempts), then fail

**Implementation**:
```groovy
@Retryable(
    attempts = '3',
    delay = '1s',
    multiplier = '2.0',
    includes = [StsException]
)
Mono<AssumeRoleResponse> assumeRoleWithRetry(AssumeRoleRequest request) {
    // ...
}
```

### 5. Metrics and Monitoring

**Question**: What metrics should be exposed?

**Status**: ✅ DECIDED - Use Micrometer (Wave's existing metrics framework)

**Metrics to Track**:
- `wave.sts.assume_role.calls` (counter) - tags: success/failure, region
- `wave.sts.assume_role.duration` (timer) - STS call latency
- `wave.ecr.auth.cache.hit_rate` (gauge) - Cache hit rate %
- `wave.ecr.auth.method` (counter) - tags: static/role - Auth method usage
- `wave.ecr.credentials.expiring_soon` (gauge) - # credentials expiring in <5 min

## Dependencies Required

### New Dependencies

**STS SDK** (already added based on git status):
```gradle
implementation 'software.amazon.awssdk:sts:2.20.+'
```

**Notes**:
- AWS SDK BOM (Bill of Materials) should manage version consistency
- STS SDK is small (~2 MB) and has minimal transitive dependencies
- Compatible with existing AWS SDK v2 usage in Wave

### Existing Dependencies (No Changes)

- AWS SDK ECR: Already present for ECR operations
- Caffeine: Already used for caching
- Micronaut Reactor: Already used for async operations
- Micrometer: Already used for metrics

## Platform Changes Required

### 1. AwsSecurityKeys Domain Class Extension

**File**: `tower-core/src/main/groovy/io/seqera/tower/domain/aws/AwsSecurityKeys.groovy:74`

**Changes**:
```groovy
class AwsSecurityKeys {
    String registry
    String username        // Can be access key ID or role ARN
    String password        // Secret key (unused for role-based auth)
    String assumeRoleArn   // Existing, may be redundant with username
    String externalId      // NEW - auto-generated UUID

    // NEW method: Generate external ID on creation
    @PrePersist
    void generateExternalId() {
        if (externalId == null && isRoleBasedAuth()) {
            externalId = UUID.randomUUID().toString()
        }
    }

    boolean isRoleBasedAuth() {
        return username?.matches(/^arn:aws:iam::\d{12}:role\/.+/)
    }
}
```

### 2. Database Migration

**Create Migration**: Add `external_id` column to `aws_security_keys` table

```sql
-- V###__add_external_id_to_aws_security_keys.sql
ALTER TABLE aws_security_keys
ADD COLUMN external_id VARCHAR(128);

-- Add index for potential lookups
CREATE INDEX idx_aws_security_keys_external_id
ON aws_security_keys(external_id);
```

### 3. Validator Extension

**File**: `tower-enterprise/src/main/groovy/io/seqera/tower/service/platform/aws/AwsSecurityKeysValidator.groovy`

**Changes**:
```groovy
class AwsSecurityKeysValidator {

    void validate(AwsSecurityKeys keys) {
        // Existing validations...

        // NEW: Validate role ARN format if provided
        if (keys.isRoleBasedAuth()) {
            validateRoleArn(keys.username)
            validateExternalId(keys.externalId)
        }
    }

    void validateRoleArn(String roleArn) {
        if (!roleArn?.matches(/^arn:aws:iam::\d{12}:role\/.+/)) {
            throw new ValidationException("Invalid IAM role ARN format")
        }
    }

    void validateExternalId(String externalId) {
        if (externalId == null) {
            throw new ValidationException("External ID is required for role-based authentication")
        }
        if (externalId.length() < 2 || externalId.length() > 1224) {
            throw new ValidationException("External ID must be 2-1224 characters")
        }
        if (!externalId.matches(/[\w+=,.@:\/-]*/)) {
            throw new ValidationException("External ID contains invalid characters")
        }
    }
}
```

## Performance Considerations

### STS API Latency

**Expected Latency**: 200-500ms for AssumeRole calls (AWS SDK overhead + network + STS processing)

**Impact**: Minimal due to caching
- First request per role: +200-500ms (one-time cost)
- Subsequent requests: Cache hit, no STS call
- Cache hit rate: Expected >95% (credentials valid for 1 hour)

### Cache Memory Overhead

**Per-Credential Entry**: ~200 bytes (access key + secret + token + timestamps)

**Scale Estimate**:
- 1000 concurrent customers = ~200 KB memory
- 10,000 concurrent customers = ~2 MB memory
- Negligible compared to Wave's typical heap (2 GB)

### Concurrent AssumeRole Requests

**Concern**: Multiple requests for same uncached role arriving simultaneously

**Mitigation**: Caffeine's `get(key, mappingFunction)` ensures only one thread performs the load
- Other threads block waiting for the result
- No duplicate STS calls for the same key

## Security Considerations

### Confused Deputy Attack Prevention

**Threat**: Attacker tricks Wave into accessing their resources using Wave's credentials

**Mitigation**: External ID requirement
- Each workspace has unique UUID external ID
- Customer's IAM role trust policy MUST specify this exact external ID
- Without correct external ID, STS denies AssumeRole request

**Trust Policy Example**:
```json
{
  "Effect": "Allow",
  "Principal": {"AWS": "arn:aws:iam::WAVE_ACCOUNT:role/WaveServiceRole"},
  "Action": "sts:AssumeRole",
  "Condition": {
    "StringEquals": {"sts:ExternalId": "a1b2c3d4-..."}
  }
}
```

### Credential Exposure Risk

**Concern**: Temporary credentials logged or exposed

**Mitigation**:
- Never log `secretAccessKey` or `sessionToken` in plaintext
- Use `[REDACTED]` placeholder in logs
- Implement custom `toString()` for credential classes to prevent accidental logging

```groovy
@Canonical
class AwsCreds {
    String accessKey
    String secretKey
    String sessionToken

    @Override
    String toString() {
        "AwsCreds(accessKey=${accessKey?.take(4)}..., secretKey=[REDACTED], sessionToken=[REDACTED])"
    }
}
```

### Session Token Validation

**Concern**: Session token corruption or tampering

**Mitigation**:
- AWS SDK validates session token format
- ECR API calls fail immediately with 401 if token invalid
- Automatic retry with fresh credentials on 401 response

## Rollout Strategy

### Phase 1: Wave Changes Only (This Repository)

**Week 1-2**: Implement core STS functionality in Wave
- Add STS client and role ARN detection
- Implement credential caching with expiration
- Add comprehensive tests (unit + integration)

**Deliverable**: Wave can authenticate using role ARN + external ID (passed as parameters)

### Phase 2: Platform Changes (nf-tower-cloud)

**Week 2-3**: Extend Platform to support external ID
- Add `externalId` field to AwsSecurityKeys
- Create database migration
- Update validators and UI

**Deliverable**: Platform can configure and store role-based credentials with external ID

### Phase 3: Integration Testing

**Week 3-4**: End-to-end testing across Platform + Wave
- Test role-based authentication flow
- Test backward compatibility with static credentials
- Performance and load testing

**Deliverable**: Verified working system across both repositories

### Phase 4: Production Rollout

**Week 4-5**: Gradual deployment with monitoring
- Deploy Wave changes first (backward compatible)
- Deploy Platform changes
- Monitor metrics and error rates
- Customer communication and documentation

**Deliverable**: Feature live in production

## References

- [AWS STS AssumeRole API](https://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRole.html)
- [AWS SDK for Java 2.x Developer Guide](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/)
- [Confused Deputy Problem](https://docs.aws.amazon.com/IAM/latest/UserGuide/confused-deputy.html)
- [AWS IAM External ID](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_create_for-user_externalid.html)
- Wave CLAUDE.md: Project conventions and architecture patterns
- Wave Constitution: Service-oriented architecture principles