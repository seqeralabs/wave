# Service Interface Contract: AwsEcrService

## Overview

This document defines the internal service interface for `AwsEcrService` supporting both static credential and role-based authentication flows, including jump role chaining.

## AwsEcrService Public Method

### getLoginToken

```groovy
@Singleton
class AwsEcrService {

    /**
     * Get ECR login token for authentication.
     * Detects whether accessKey is a role ARN or static access key
     * and dispatches to the appropriate flow.
     *
     * @param accessKey AWS access key ID OR IAM role ARN
     * @param secretKey AWS secret access key OR external ID
     * @param region AWS region derived from ECR registry hostname
     * @param isPublic true for ECR Public (public.ecr.aws), false for private ECR
     * @return ECR authorization token (Base64-decoded username:password)
     */
    String getLoginToken(String accessKey, String secretKey, String region, boolean isPublic)
}
```

**Behavior Change** (internal, not breaking):
- **Before**: `accessKey` only accepted access key ID format
- **After**: `accessKey` accepts EITHER access key ID OR role ARN
- Detection is automatic via `isRoleArn()` pattern matching — no API change

---

## Internal Method: isRoleArn

```groovy
protected static boolean isRoleArn(String accessKey) {
    return accessKey?.matches(AWS_ROLE_ARN)
}
```

**Pattern**: `^arn:aws(-cn|-us-gov)?:iam::\d{12}:role\/[\w+=,.@\/-]+$`

**Returns**:
- `true` if accessKey matches role ARN pattern (including AWS China and GovCloud partitions)
- `false` if accessKey is a static access key ID or null

---

## Internal Method: getLoginTokenWithRole

```groovy
protected String getLoginTokenWithRole(String roleArn, String externalId, String region, boolean isPublic) {
    final key = AwsCreds.ofRole(roleArn, externalId, region, isPublic)
    return cache.getOrCompute(key, (String k) -> {
        return assumeRoleAndLoadToken(roleArn, externalId, region, isPublic)
    }).value
}
```

**Purpose**: Assume an IAM role, obtain temporary credentials, then get ECR auth token. Uses `Pair<AwsEcrAuthToken, Duration>` for dynamic per-entry TTL based on STS credential expiration.

---

## Internal Method: getLoginTokenWithStaticCredentials

```groovy
protected String getLoginTokenWithStaticCredentials(String accessKey, String secretKey, String region, boolean isPublic) {
    final key = AwsCreds.ofKeys(accessKey, secretKey, null, region, isPublic)
    return cache.getOrCompute(key, (k) -> load(key), cache.duration).value
}
```

**Purpose**: Authenticate using static AWS credentials (backward compatible). Uses default `cache.duration` for TTL.

---

## Internal Method: assumeRoleAndLoadToken

```groovy
protected AbstractTieredCache.Pair<AwsEcrAuthToken, Duration> assumeRoleAndLoadToken(
        String roleArn, String externalId, String region, boolean isPublic) {
    final tempCreds = assumeRole(roleArn, externalId, region)
    final ttl = computeCacheTtl(tempCreds.expiration(), cache.duration)
    final tempKey = AwsCreds.ofKeys(
            tempCreds.accessKeyId(), tempCreds.secretAccessKey(),
            tempCreds.sessionToken(), region, isPublic)
    final token = load(tempKey)
    return new AbstractTieredCache.Pair<AwsEcrAuthToken, Duration>(token, ttl)
}
```

**Purpose**: Combines STS AssumeRole + ECR GetAuthorizationToken into a single cache-loading operation. STS exceptions are mapped to `AwsEcrAuthException` via `mapStsException()`.

---

## Internal Method: load

```groovy
AwsEcrAuthToken load(AwsCreds creds) throws Exception {
    assert !creds.roleArn, "Cannot load ECR token from role-based cache key — use temporary credentials"
    def token = creds.ecrPublic
            ? getLoginToken1(creds.accessKey, creds.secretKey, creds.sessionToken, creds.region)
            : getLoginToken0(creds.accessKey, creds.secretKey, creds.sessionToken, creds.region)
    return new AwsEcrAuthToken(token)
}
```

**Purpose**: Fetch ECR auth token using concrete credentials (static or temporary from STS). The assertion guard prevents accidental use with role-based cache keys.

---

## Jump Role Chaining

### assumeRole

```groovy
protected Credentials assumeRole(String roleArn, String externalId, String region) {
    // No jump role configured — assume target role directly
    if (!jumpRoleArn) {
        return stsClient(region).withCloseable { client ->
            assumeTargetRole(client, roleArn, externalId)
        }
    }

    // Jump role chaining: jump role → target role
    final jumpCreds = assumeJumpRole(region)
    try {
        return stsClient(region, jumpCreds).withCloseable { client ->
            assumeTargetRole(client, roleArn, externalId)
        }
    }
    catch (StsException e) {
        // Retry once on expired token — bypass cache to get fresh jump role credentials
        if (e.awsErrorDetails()?.errorCode() == 'ExpiredTokenException') {
            final freshCreds = doAssumeJumpRole(region)
            return stsClient(region, freshCreds).withCloseable { freshClient ->
                assumeTargetRole(freshClient, roleArn, externalId)
            }
        }
        throw e
    }
}
```

**Purpose**: Two-hop role assumption. When `wave.aws.jump-role-arn` is configured, Wave first assumes the jump role (cached via `AwsRoleCache`), then uses those temporary credentials to assume the customer's target role. Handles expired token race condition with a single cache-bypass retry (FR-011).

### assumeJumpRole

```groovy
protected Credentials assumeJumpRole(String region) {
    return jumpRoleCache.getOrCompute(region, (String k) -> {
        final creds = doAssumeJumpRole(region)
        final cached = AwsStsCredentials.from(creds)
        final ttl = computeCacheTtl(creds.expiration(), jumpRoleCache.duration)
        return new AbstractTieredCache.Pair<AwsStsCredentials, Duration>(cached, ttl)
    }).toSdkCredentials()
}
```

**Purpose**: Get jump role credentials from cache or assume them fresh. Uses `AwsRoleCache` (extends `AbstractTieredCache<String, AwsStsCredentials>`) with dynamic TTL derived from STS credential expiration.

### doAssumeJumpRole

```groovy
protected Credentials doAssumeJumpRole(String region) {
    return stsClient(region).withCloseable { client ->
        doAssumeRole(client, jumpRoleArn, jumpExternalId, "wave-jump")
    }
}
```

**Purpose**: Directly assume jump role using Wave's default credentials, bypassing cache. Used both as cache loader and as fallback for expired token retry.

---

## STS Client Factory Methods

```groovy
// Default credentials (Wave's own IAM role/env credentials)
protected static StsClient stsClient(String region)

// Session credentials (from a prior role assumption, e.g., jump role)
protected static StsClient stsClient(String region, Credentials credentials)
```

**Purpose**: Per-region STS clients. No singleton bean — clients are created per-call and closed via `withCloseable`.

---

## Retry and Error Handling

### Retry Strategy

```groovy
private Credentials doAssumeRole(StsClient client, String roleArn, String externalId, String sessionPrefix) {
    Retryable.<Credentials>of(stsConfig)
            .retryCondition((Throwable t) -> isRetryableStsError(t))
            .onRetry((event) -> log.debug("STS AssumeRole retry for $roleArn - attempt: ${event.attempt}"))
            .apply(() -> {
                final request = buildAssumeRoleRequest(roleArn, externalId, sessionPrefix)
                return client.assumeRole(request).credentials()
            })
}
```

**Retry config** (`StsClientConfig` implementing `Retryable.Config`):
- `wave.aws.sts.retry.delay`: 1s
- `wave.aws.sts.retry.maxDelay`: 10s
- `wave.aws.sts.retry.attempts`: 3
- `wave.aws.sts.retry.multiplier`: 2.0
- `wave.aws.sts.retry.jitter`: 0.25

**Retryable errors**: 5xx server errors and `Throttling` error code.

### Error Mapping

```groovy
protected static Exception mapStsException(StsException e) {
    switch (e.awsErrorDetails()?.errorCode()) {
        case 'AccessDenied':         → AwsEcrAuthException (trust policy guidance)
        case 'InvalidParameterValue': → AwsEcrAuthException (ARN format guidance)
        case 'RegionDisabledException': → AwsEcrAuthException (STS endpoint guidance)
        case 'ExpiredTokenException':   → AwsEcrAuthException (retry guidance)
        default:                        → AwsEcrAuthException (generic STS failure)
    }
}
```

All STS errors are mapped to `AwsEcrAuthException` with user-friendly messages.

---

## Session Name Generation

```groovy
protected static AssumeRoleRequest buildAssumeRoleRequest(String roleArn, String externalId, String sessionPrefix) {
    // Session name format: {prefix}-{accountId}-{timestamp}
    // e.g., "wave-ecr-123456789012-1707494400000" or "wave-jump-123456789012-1707494400000"
    final builder = AssumeRoleRequest.builder()
            .roleArn(roleArn)
            .roleSessionName("${sessionPrefix}-${extractAccountId(roleArn)}-${System.currentTimeMillis()}")
            .durationSeconds(SESSION_DURATION_SECONDS)
    if (externalId) {
        builder.externalId(externalId)
    }
    return builder.build()
}
```

**Purpose**: Generate unique session names for CloudTrail auditing. External ID is only set when provided (optional per spec).

---

## AwsCreds Value Object (Cache Key)

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

**Changes from original**:
- Dedicated `roleArn`/`externalId` fields (no longer overloads `accessKey`/`secretKey`)
- Factory methods `ofRole()` and `ofKeys()` for self-documenting construction
- `stableHash()` branches on `roleArn` presence; sessionToken only included when present for backward compatibility
- Implements `TieredKey` interface for `AbstractTieredCache` compatibility

---

## Cache Architecture

### AwsEcrCache (ECR Auth Tokens)

```groovy
class AwsEcrCache extends AbstractTieredCache<AwsCreds, AwsEcrAuthToken> {
    // Config: wave.aws.ecr.cache.{duration, max-size:10000}
    // Supports Pair<AwsEcrAuthToken, Duration> for dynamic per-entry TTL
}
```

### AwsRoleCache (Jump Role STS Credentials)

```groovy
class AwsRoleCache extends AbstractTieredCache<String, AwsStsCredentials> {
    // Config: wave.aws.jump-role-cache.{duration:45m, max-size:100}
    // Key: region string
    // Value: AwsStsCredentials (MoshiSerializable wrapper for STS Credentials)
}
```

### Cache TTL Computation

```groovy
protected static Duration computeCacheTtl(Instant expiration, Duration maxDuration) {
    if (expiration == null) return maxDuration
    final timeUntilExpiry = Duration.between(Instant.now(), expiration)
    final bufferedTtl = timeUntilExpiry.minus(REFRESH_BUFFER)  // 5-minute buffer
    if (bufferedTtl.compareTo(MIN_CACHE_TTL) < 0) return MIN_CACHE_TTL  // 1-minute floor
    return bufferedTtl.compareTo(maxDuration) < 0 ? bufferedTtl : maxDuration
}
```

No `isExpiring()` method — TTL-based cache eviction handles credential lifecycle. When the TTL expires, the next request triggers a fresh `assumeRole()` + ECR token load.

---

## Dependency Injection

```groovy
@Inject private AwsEcrCache cache           // ECR auth token cache
@Inject private AwsRoleCache jumpRoleCache   // Jump role STS credential cache
@Inject private StsClientConfig stsConfig    // Retry configuration

@Nullable @Value('${wave.aws.jump-role-arn}')
private String jumpRoleArn                   // Jump role ARN (optional)

@Nullable @Value('${wave.aws.jump-external-id}')
private String jumpExternalId                // Jump role external ID (optional)
```

---

## Backward Compatibility Guarantees

### Static Credentials Continue to Work

```groovy
// Before feature (still works)
def token1 = awsEcrService.getLoginToken('AKIAIOSFODNN7EXAMPLE', 'secretKey', 'us-east-1', false)

// After feature (new capability)
def token2 = awsEcrService.getLoginToken('arn:aws:iam::123456789012:role/WaveEcrAccess', 'ext-id', 'us-east-1', false)

// Both work correctly
assert token1 != null
assert token2 != null
```

### No Breaking Changes

| Aspect | Before | After | Breaking? |
|--------|--------|-------|-----------|
| Method signature | `getLoginToken(String, String, String, boolean)` | Same | No |
| Return type | `String` (ECR token) | Same | No |
| Exception types | `AwsEcrAuthException` | Same + more specific messages | No |
| Cache behavior | Returns cached token | Same + STS credential caching | No |
| Performance (cache hit) | <100ms | Same | No |

---

## Performance Contract

### Latency Expectations

| Operation | Target (p95) | Notes |
|-----------|--------------|-------|
| `getLoginToken` (cache hit, static) | <100ms | Existing performance |
| `getLoginToken` (cache hit, role) | <100ms | Same as static (cached) |
| `getLoginToken` (cache miss, static) | <500ms | ECR API call |
| `getLoginToken` (cache miss, role) | <1000ms | STS + ECR API calls |
| `getLoginToken` (cache miss, jump role) | <1500ms | Jump STS + Target STS + ECR |

### Cache Hit Rate

| Scenario | Expected Hit Rate | Notes |
|----------|-------------------|-------|
| Typical workload | >95% | 1 request per 5 min, 1 hr credentials |
| Heavy workload | >99% | 10+ requests per 5 min |
| First request | 0% | Always cache miss |

---

## References

- `src/main/groovy/io/seqera/wave/service/aws/AwsEcrService.groovy` - Service implementation
- `src/main/groovy/io/seqera/wave/service/aws/cache/AwsEcrCache.groovy` - ECR auth token cache
- `src/main/groovy/io/seqera/wave/service/aws/cache/AwsRoleCache.groovy` - Jump role credential cache
- `src/main/groovy/io/seqera/wave/service/aws/cache/AwsStsCredentials.groovy` - STS credential wrapper
- `src/main/groovy/io/seqera/wave/service/aws/cache/AwsEcrAuthToken.groovy` - ECR auth token wrapper
- `src/main/groovy/io/seqera/wave/service/aws/StsClientConfig.groovy` - STS retry config
- [AWS SDK STS Documentation](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/sts/package-summary.html)