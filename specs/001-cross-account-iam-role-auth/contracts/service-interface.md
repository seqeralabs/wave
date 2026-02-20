# Service Interface Contract: AwsEcrService

## Overview

This document defines the internal service interface changes for `AwsEcrService` to support role-based authentication.

## AwsEcrService Methods

### Existing Method (Unchanged Interface)

```groovy
@Singleton
class AwsEcrService {

    /**
     * Get ECR login token for authentication
     *
     * @param registry ECR registry URL (e.g., "123456789012.dkr.ecr.us-east-1.amazonaws.com")
     * @param username AWS access key ID OR IAM role ARN
     * @param password AWS secret access key OR external ID
     * @return ECR authorization token
     */
    String getLoginToken(String registry, String username, String password)
}
```

**Behavior Change** (internal, not breaking):
- **Before**: `username` only accepted access key ID format
- **After**: `username` accepts EITHER access key ID OR role ARN
- Detection is automatic via pattern matching - no API change

---

## Internal Implementation Changes

### New Private Method: isRoleArn

```groovy
private boolean isRoleArn(String username) {
    return username?.matches(/^arn:aws:iam::\d{12}:role\/.+/)
}
```

**Purpose**: Detect whether username is a role ARN or access key ID

**Returns**:
- `true` if username matches role ARN pattern
- `false` if username is access key ID or null

---

### New Private Method: assumeRoleAndAuthenticate

```groovy
private String assumeRoleAndAuthenticate(String registry, String roleArn, String externalId) {
    // 1. Parse region from registry
    String region = extractRegion(registry)

    // 2. Build AssumeRole request
    AssumeRoleRequest request = AssumeRoleRequest.builder()
        .roleArn(roleArn)
        .roleSessionName(generateSessionName(registry))
        .externalId(externalId)
        .durationSeconds(3600)
        .build()

    // 3. Call STS AssumeRole
    AssumeRoleResponse response = stsClient.assumeRole(request)
    Credentials creds = response.credentials()

    // 4. Cache temporary credentials
    AwsCreds awsCreds = new AwsCreds(
        accessKey: creds.accessKeyId(),
        secretKey: creds.secretAccessKey(),
        sessionToken: creds.sessionToken(),
        region: region,
        ecrPublic: false
    )

    CachedEcrCredentials cached = new CachedEcrCredentials(
        accessKeyId: creds.accessKeyId(),
        secretAccessKey: creds.secretAccessKey(),
        sessionToken: creds.sessionToken(),
        stsExpiration: creds.expiration(),
        authToken: null,  // To be filled
        tokenExpiration: null  // To be filled
    )

    // 5. Get ECR authorization token using temporary credentials
    String ecrToken = getEcrAuthToken(awsCreds)

    // 6. Update cache entry with ECR token
    cached.authToken = ecrToken
    cached.tokenExpiration = Instant.now().plus(Duration.ofHours(12))

    // 7. Store in cache
    ecrCache.put(awsCreds.stableHash(), cached)

    return ecrToken
}
```

---

### New Private Method: generateSessionName

```groovy
private String generateSessionName(String registry) {
    // Extract registry account ID and region
    def matcher = registry =~ /^(\d{12})\.dkr\.ecr\.([a-z0-9-]+)\./
    if (matcher) {
        String accountId = matcher[0][1]
        return "wave-ecr-${accountId}-${System.currentTimeMillis()}"
    }

    // Fallback for unexpected format
    return "wave-ecr-access-${System.currentTimeMillis()}"
}
```

**Purpose**: Generate unique session name for CloudTrail auditing

**Output Format**: `wave-ecr-{accountId}-{timestamp}`

**Example**: `wave-ecr-123456789012-1707494400000`

---

### Modified Method: getLoginToken (Internal Logic)

```groovy
String getLoginToken(String registry, String username, String password) {
    // NEW: Detect authentication type
    if (isRoleArn(username)) {
        // Role-based authentication
        log.debug("Detected role ARN, using STS AssumeRole authentication")
        return assumeRoleAndAuthenticate(registry, username, password)
    } else {
        // Static credential authentication (existing flow)
        log.debug("Detected access key ID, using static credential authentication")
        return authenticateWithStaticCredentials(registry, username, password)
    }
}
```

---

## AwsCreds Value Object

### Modified Structure

```groovy
@Canonical
class AwsCreds {
    String accessKey
    String secretKey
    String sessionToken    // NEW - null for static credentials
    String region
    boolean ecrPublic

    String stableHash() {
        return Hashing.sha256()
            .hashString(
                "$accessKey:$secretKey:${sessionToken ?: ''}:$region:$ecrPublic",
                Charsets.UTF_8
            )
            .toString()
    }

    @Override
    String toString() {
        return "AwsCreds(" +
            "accessKey=${accessKey?.take(8)}..., " +
            "secretKey=[REDACTED], " +
            "sessionToken=${sessionToken ? '[REDACTED]' : 'null'}, " +
            "region=$region, " +
            "ecrPublic=$ecrPublic)"
    }
}
```

**Changes**:
- Add `sessionToken` field (String, nullable)
- Update `stableHash()` to include session token
- Override `toString()` to redact sensitive fields

---

## CachedEcrCredentials Value Object

### Modified Structure

```groovy
@Canonical
class CachedEcrCredentials {
    String accessKeyId
    String secretAccessKey
    String sessionToken        // NEW - null for static credentials
    Instant stsExpiration      // NEW - null for static credentials
    String authToken
    Instant tokenExpiration

    boolean isExpiring() {
        def now = Instant.now()
        def buffer = Duration.ofMinutes(5)

        // Check STS expiration (role-based auth)
        if (stsExpiration != null && now.plus(buffer).isAfter(stsExpiration)) {
            return true
        }

        // Check ECR token expiration (both auth types)
        return now.plus(buffer).isAfter(tokenExpiration)
    }

    Instant getEffectiveExpiration() {
        if (stsExpiration == null) {
            return tokenExpiration
        }
        return stsExpiration.isBefore(tokenExpiration) ? stsExpiration : tokenExpiration
    }
}
```

**Changes**:
- Add `sessionToken` field (String, nullable)
- Add `stsExpiration` field (Instant, nullable)
- Add `isExpiring()` method
- Add `getEffectiveExpiration()` method

---

## Dependency Injection

### New Bean: StsClient

```groovy
@Factory
class AwsStsConfig {

    @Singleton
    StsClient stsClient() {
        return StsClient.builder()
            .region(Region.AWS_GLOBAL)  // Use global STS endpoint
            .build()
    }
}
```

**Configuration**:
- Uses global STS endpoint for simplicity
- Can be overridden with regional endpoints later
- Automatically handles AWS credentials from environment/IAM role

---

## Error Handling

### New Exception Mapping

```groovy
private String assumeRoleAndAuthenticate(String registry, String roleArn, String externalId) {
    try {
        AssumeRoleResponse response = stsClient.assumeRole(request)
        // ... success path
    } catch (StsException e) {
        switch (e.awsErrorDetails().errorCode()) {
            case 'AccessDenied':
                throw new UnauthorizedException(
                    "Wave's service role cannot assume the specified IAM role. " +
                    "Verify the trust policy allows Wave's service role and includes the correct external ID.",
                    e
                )
            case 'InvalidParameterValue':
                throw new BadRequestException(
                    "Invalid role ARN or external ID format: ${e.awsErrorDetails().errorMessage()}",
                    e
                )
            case 'RegionDisabledException':
                throw new BadRequestException(
                    "STS is not enabled in the specified region. " +
                    "Enable STS endpoints for this region in your AWS account.",
                    e
                )
            case 'ExpiredToken':
                log.warn("Temporary credentials expired, invalidating cache and retrying")
                cache.invalidate(credKey)
                throw new UnauthorizedException("Temporary credentials expired. Please retry.", e)
            default:
                throw new WaveException(
                    "STS AssumeRole failed: ${e.awsErrorDetails().errorMessage()}",
                    e
                )
        }
    }
}
```

---

## Caching Strategy

### Cache Lookup

```groovy
CachedEcrCredentials getCachedCredentials(AwsCreds creds) {
    String key = creds.stableHash()

    CachedEcrCredentials cached = ecrCache.get(key)

    // Return null if not cached or expiring soon
    if (cached == null || cached.isExpiring()) {
        ecrCache.invalidate(key)
        return null
    }

    return cached
}
```

### Cache Concurrency

```groovy
CachedEcrCredentials getOrLoad(AwsCreds creds) {
    return ecrCache.get(creds.stableHash()) { key ->
        // Only one thread executes this block per key
        // Other threads block waiting for result
        return loadCredentialsAndAuthenticate(creds)
    }
}
```

**Guarantees**:
- No duplicate STS calls for same credential
- Thread-safe concurrent access
- Automatic retry on transient failures

---

## Backward Compatibility Guarantees

### Static Credentials Continue to Work

```groovy
// Before feature (still works)
def token1 = awsEcrService.getLoginToken(
    '123456789012.dkr.ecr.us-east-1.amazonaws.com',
    'AKIAIOSFODNN7EXAMPLE',
    'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY'
)

// After feature (new capability)
def token2 = awsEcrService.getLoginToken(
    '123456789012.dkr.ecr.us-east-1.amazonaws.com',
    'arn:aws:iam::123456789012:role/WaveEcrAccess',
    'a1b2c3d4-e5f6-7890-abcd-ef1234567890'
)

// Both work correctly
assert token1 != null
assert token2 != null
```

### No Breaking Changes

| Aspect | Before | After | Breaking? |
|--------|--------|-------|-----------|
| Method signature | `getLoginToken(String, String, String)` | Same | ❌ No |
| Return type | `String` (ECR token) | Same | ❌ No |
| Exception types | `UnauthorizedException`, `WaveException` | Same + more specific messages | ❌ No |
| Cache behavior | Returns cached token | Same + STS credential caching | ❌ No |
| Performance | ~100ms (cache hit) | Same | ❌ No |

---

## Testing Contract

### Unit Test Requirements

```groovy
class AwsEcrServiceSpec extends Specification {

    def "should detect role ARN pattern"() {
        given:
        def service = new AwsEcrService()

        expect:
        service.isRoleArn('arn:aws:iam::123456789012:role/MyRole') == true
        service.isRoleArn('AKIAIOSFODNN7EXAMPLE') == false
        service.isRoleArn(null) == false
    }

    def "should call STS AssumeRole for role ARN"() {
        given:
        def stsClient = Mock(StsClient)
        def service = new AwsEcrService(stsClient: stsClient)

        when:
        service.getLoginToken(registry, roleArn, externalId)

        then:
        1 * stsClient.assumeRole(_) >> mockAssumeRoleResponse()
    }

    def "should use static credentials for access key"() {
        given:
        def stsClient = Mock(StsClient)
        def service = new AwsEcrService(stsClient: stsClient)

        when:
        service.getLoginToken(registry, accessKey, secretKey)

        then:
        0 * stsClient.assumeRole(_)  // No STS call for static credentials
    }

    def "should cache temporary credentials with expiration"() {
        given:
        def service = new AwsEcrService()
        def creds = createStsCredentials(expiresIn: Duration.ofHours(1))

        when:
        service.cacheCredentials(creds)

        then:
        service.getCachedCredentials(creds) != null
        !service.getCachedCredentials(creds).isExpiring()
    }

    def "should invalidate expiring credentials"() {
        given:
        def service = new AwsEcrService()
        def creds = createStsCredentials(expiresIn: Duration.ofMinutes(4))

        when:
        service.cacheCredentials(creds)

        then:
        service.getCachedCredentials(creds) == null  // Treated as expired
    }
}
```

---

## Performance Contract

### Latency Expectations

| Operation | Target (p95) | Notes |
|-----------|--------------|-------|
| `getLoginToken` (cache hit, static) | <100ms | Existing performance |
| `getLoginToken` (cache hit, role) | <100ms | Same as static (cached) |
| `getLoginToken` (cache miss, static) | <500ms | ECR API call |
| `getLoginToken` (cache miss, role) | <1000ms | STS + ECR API calls |

### Cache Hit Rate

| Scenario | Expected Hit Rate | Notes |
|----------|-------------------|-------|
| Typical workload | >95% | 1 request per 5 min, 1 hr credentials |
| Heavy workload | >99% | 10+ requests per 5 min |
| First request | 0% | Always cache miss |

---

## References

- `src/main/groovy/io/seqera/wave/service/aws/AwsEcrService.groovy` - Service implementation
- `src/main/groovy/io/seqera/wave/service/aws/cache/AwsEcrCache.groovy` - Cache implementation
- [AWS SDK STS Documentation](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/sts/package-summary.html)