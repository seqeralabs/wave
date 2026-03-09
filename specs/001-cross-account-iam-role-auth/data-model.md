# Data Model: Cross-Account IAM Role Authentication (Wave)

## Overview

Wave's data model changes are minimal and focused on in-memory structures for credential caching. There are **no database changes** in Wave - all persistence is handled by Platform.

## Modified Classes

### 1. AwsCreds (Inner Class in AwsEcrService)

**Location**: `src/main/groovy/io/seqera/wave/service/aws/AwsEcrService.groovy:96`

**Purpose**: Cache key for ECR credentials

**Current Structure**:
```groovy
@Canonical
class AwsCreds {
    String accessKey
    String secretKey
    String region
    boolean ecrPublic
}
```

**Modified Structure**:
```groovy
@Canonical
class AwsCreds {
    // AWS access key ID (static credentials flow)
    String accessKey
    // AWS secret access key (static credentials flow)
    String secretKey
    // IAM role ARN (role-based auth flow)
    String roleArn
    // External ID for cross-account role assumption (role-based auth flow)
    String externalId
    // Temporary session token from STS AssumeRole
    String sessionToken
    // AWS region derived from the ECR registry hostname
    String region
    // true for ECR Public (public.ecr.aws), false for private ECR
    boolean ecrPublic

    String stableHash() {
        if (roleArn) {
            return RegHelper.sipHash(roleArn, externalId, region, ecrPublic)
        }
        // sessionToken only included when present to preserve backward-compatible hash values
        final args = [accessKey, secretKey, region, ecrPublic] as List<Object>
        if (sessionToken) args.add(sessionToken)
        return RegHelper.sipHash(args.toArray())
    }

    static AwsCreds ofRole(String roleArn, String externalId, String region, boolean ecrPublic) {
        new AwsCreds(roleArn: roleArn, externalId: externalId, region: region, ecrPublic: ecrPublic)
    }

    static AwsCreds ofKeys(String accessKey, String secretKey, String sessionToken, String region, boolean ecrPublic) {
        new AwsCreds(accessKey: accessKey, secretKey: secretKey, sessionToken: sessionToken, region: region, ecrPublic: ecrPublic)
    }
}
```

**Changes**:
- Separate `roleArn`/`externalId` fields for role-based auth (no longer overloads `accessKey`/`secretKey`)
- Factory methods `ofRole()` and `ofKeys()` for self-documenting construction
- `stableHash()` uses different fields per flow; sessionToken only included when present for backward compatibility

---

### 2. AwsEcrAuthToken (Cache Value)

**Location**: `src/main/groovy/io/seqera/wave/service/aws/cache/AwsEcrAuthToken.groovy`

**Purpose**: Stores the ECR authorization token as the cache value in `AwsEcrCache`

**Structure**:
```groovy
class AwsEcrAuthToken {
    String value  // ECR authorization token (Base64-decoded username:password)
}
```

**No `isExpiring()` method** — credential lifecycle is managed entirely via cache TTL computed by `computeCacheTtl()`. When the cache entry expires (TTL = STS expiration minus 5-minute buffer), the next request triggers a fresh `assumeRole()` + ECR token load.

### 3. AwsStsCredentials (Jump Role Cache Value)

**Location**: `src/main/groovy/io/seqera/wave/service/aws/cache/AwsStsCredentials.groovy`

**Purpose**: MoshiSerializable wrapper for STS temporary credentials, used as cache value in `AwsRoleCache`

**Structure**:
```groovy
@MoshiSerializable
class AwsStsCredentials {
    String accessKeyId
    String secretAccessKey
    String sessionToken
    long expirationEpochMilli  // long for Moshi compatibility (not Instant)

    static AwsStsCredentials from(Credentials creds) { /* ... */ }
    Credentials toSdkCredentials() { /* ... */ }
    Instant expiration() { Instant.ofEpochMilli(expirationEpochMilli) }
}
```

---

## Data Flow

### Authentication with Role-Based Credentials

```
1. Platform sends: assumeRoleArn + externalId in JSON payload
   Wave maps: assumeRoleArn → userName, externalId → password (via ContainerRegistryKeys.fromJson())
                        ↓
2. Wave detects role ARN pattern in userName via isRoleArn()
                        ↓
3. Wave creates cache key: AwsCreds.ofRole(roleArn, externalId, region, false)
   stableHash() uses roleArn + externalId (stable across STS refreshes)
                        ↓
4. Cache miss → assumeRoleAndLoadToken():
   a. Calls assumeRole(roleArn, externalId, region) → STS AssumeRole
   b. AWS STS returns: accessKeyId, secretAccessKey, sessionToken, expiration
   c. Creates temp key: AwsCreds.ofKeys(accessKeyId, secretAccessKey, sessionToken, region, false)
   d. Calls load(tempKey) → ECR GetAuthorizationToken using session credentials
   e. Computes TTL: computeCacheTtl(expiration, maxDuration) → ~55 minutes
   f. Returns Pair<AwsEcrAuthToken, Duration> with dynamic TTL
                        ↓
5. Wave returns ECR authToken to client
                        ↓
6. On next request within ~55 minutes:
   - Cache hit on roleArn-based key → return cached AwsEcrAuthToken immediately
                        ↓
7. After ~55 minutes (cache TTL expires):
   - Cache evicts entry automatically
   - Next request triggers cache miss → repeat from step 4
```

### Authentication with Static Credentials (Unchanged)

```
1. Platform sends: accessKey (as username), secretKey (as password)
                        ↓
2. Wave detects static credential pattern (isRoleArn returns false)
                        ↓
3. Wave creates cache key: AwsCreds.ofKeys(accessKey, secretKey, null, region, false)
   stableHash() uses accessKey + secretKey (no sessionToken for backward compat)
                        ↓
4. Cache miss → load(key) → ECR GetAuthorizationToken directly
   load() asserts !creds.roleArn before proceeding
                        ↓
5. Wave returns ECR authToken to client
```

---

## Cache Key Strategy

### Separate Fields for Role-Based vs Static Credentials

**Problem**: Overloading `accessKey`/`secretKey` for both static credentials and role ARN/external ID
made the code ambiguous and error-prone.

**Solution**: Dedicated fields for each flow with factory methods:

```groovy
// Role-based auth: cache key uses roleArn + externalId (stable across STS sessions)
roleKey = AwsCreds.ofRole('arn:aws:iam::123456789012:role/MyRole', 'ext-id', 'us-east-1', false)
hash1 = sipHash(roleArn, externalId, region, ecrPublic)

// Static credentials: cache key uses accessKey + secretKey
staticKey = AwsCreds.ofKeys('AKIA...', 'secretKey', null, 'us-east-1', false)
hash2 = sipHash(accessKey, secretKey, region, ecrPublic)

// Temporary credentials (from STS): includes sessionToken in hash only when present
tempKey = AwsCreds.ofKeys('ASIA...', 'secretKey', 'sessionToken...', 'us-east-1', false)
hash3 = sipHash(accessKey, secretKey, region, ecrPublic, sessionToken)
```

**Benefits**:
- No field overloading — each field has a single clear purpose
- Factory methods enforce which fields are set for each flow
- `load()` asserts that role-based cache keys cannot be used directly for ECR auth
- Backward-compatible hash: sessionToken only appended when present

---

## Expiration Logic

### 5-Minute Refresh Buffer

**Why 5 minutes?**
- Prevents authentication failures during active operations
- Provides buffer for STS API latency and retries
- Small performance cost for much better reliability

**Implementation** — TTL-based cache eviction via `computeCacheTtl()`:
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

No `isExpiring()` method exists — the cache entry TTL is set at write time via `AbstractTieredCache.Pair<V, Duration>`. When the TTL expires, the cache evicts the entry automatically, and the next request triggers a fresh `assumeRole()` + ECR token load.

### Cache TTL Calculation

**AbstractTieredCache with dynamic per-entry TTL**:
```groovy
// Role-based: TTL derived from STS credential expiration
cache.getOrCompute(key, (String k) -> {
    final tempCreds = assumeRole(roleArn, externalId, region)
    final ttl = computeCacheTtl(tempCreds.expiration(), cache.duration)  // ~55 min for 1-hour sessions
    final token = load(tempKey)
    return new AbstractTieredCache.Pair<AwsEcrAuthToken, Duration>(token, ttl)
})

// Static: uses default cache.duration (no STS expiration to consider)
cache.getOrCompute(key, (k) -> load(key), cache.duration)
```

---

## Memory Impact

### Per Cache Entry Size

**AwsEcrCache** (ECR auth tokens — `AwsEcrAuthToken`):

| Field | Size | Notes |
|-------|------|-------|
| Cache key (`stableHash()`) | ~32 bytes | SipHash string |
| `AwsEcrAuthToken.value` | ~1500 bytes | Base64-decoded ECR username:password |
| Cache metadata (TTL, etc.) | ~64 bytes | AbstractTieredCache overhead |
| **Total per entry** | **~1.6 KB** | Same for both role-based and static |

**AwsRoleCache** (jump role credentials — `AwsStsCredentials`):

| Field | Size | Notes |
|-------|------|-------|
| Cache key (region string) | ~16 bytes | e.g., "us-east-1" |
| `accessKeyId` | 20 bytes | ASIA... format |
| `secretAccessKey` | 40 bytes | Base64-like string |
| `sessionToken` | ~1000 bytes | JWT-like token |
| `expirationEpochMilli` | 8 bytes | long |
| Cache metadata | ~64 bytes | AbstractTieredCache overhead |
| **Total per entry** | **~1.1 KB** | One per region (max ~100) |

### Scale Estimates

**AwsEcrCache scale** (one entry per unique credential context):

| Customers | Memory |
|-----------|--------|
| 100 | 160 KB |
| 1,000 | 1.6 MB |
| 10,000 | 16 MB |

**AwsRoleCache scale** (one entry per region, max 100):

| Regions | Memory |
|---------|--------|
| 5 | 5.5 KB |
| 20 | 22 KB |

**Conclusion**: Negligible impact (Wave typically uses 2 GB heap)

---

## Backward Compatibility

### Static Credentials Keep Working

**No changes to existing flow**:
- `AwsCreds.ofKeys()` with `sessionToken: null` produces the same hash as the pre-refactor code
- `stableHash()` only appends `sessionToken` to the hash arguments when it is non-null
- `load()` assertion (`!creds.roleArn`) passes for static credentials since `roleArn` is null
- Static path uses default `cache.duration` (no `computeCacheTtl()` needed)

**Example**:
```groovy
// Static credentials (before and after feature)
def staticCreds = AwsCreds.ofKeys(
    'AKIAIOSFODNN7EXAMPLE',
    'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY',
    null,  // no session token for static credentials
    'us-east-1',
    false
)
// stableHash() = sipHash(accessKey, secretKey, region, ecrPublic) — same as before refactor

// Role-based credentials use separate factory and fields
def roleCreds = AwsCreds.ofRole(
    'arn:aws:iam::123456789012:role/WaveEcrAccess',
    'ext-id-uuid',
    'us-east-1',
    false
)
// stableHash() = sipHash(roleArn, externalId, region, ecrPublic) — stable across STS refreshes
```

---

## Summary

Wave's data model changes are **minimal and non-breaking**:

✅ **One refactored class, two new classes**:
- `AwsCreds`: Dedicated `roleArn`/`externalId` fields (no more overloading `accessKey`/`secretKey`), factory methods `ofRole()`/`ofKeys()`, `load()` assertion guard, backward-compatible hash
- `AwsEcrAuthToken`: Simple cache value wrapper for ECR auth tokens
- `AwsStsCredentials`: MoshiSerializable cache value for jump role credentials in `AwsRoleCache`

✅ **No database changes** in Wave (all persistence in Platform)

✅ **Backward compatible**: `ofKeys()` with null `sessionToken` produces identical hashes to pre-refactor code

✅ **TTL-based expiration**: `computeCacheTtl()` sets per-entry TTL at cache write time (no `isExpiring()` polling)

✅ **Low memory overhead**: ~2.6 KB per customer = ~26 MB for 10K customers

✅ **Proactive refresh**: 5-minute buffer via cache TTL prevents expiration failures