# Data Model: Cross-Account IAM Role Authentication (Wave)

## Overview

Wave's data model changes are minimal and focused on in-memory structures for credential caching. There are **no database changes** in Wave - all persistence is handled by Platform.

## Modified Classes

### 1. AwsCreds (Inner Class in AwsEcrService)

**Location**: `src/main/groovy/io/seqera/wave/service/aws/AwsEcrService.groovy:58`

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

### 2. CachedEcrCredentials (AwsEcrCache)

**Location**: `src/main/groovy/io/seqera/wave/service/aws/cache/AwsEcrCache.groovy`

**Purpose**: Stores cached ECR tokens and credentials with expiration tracking

**Current Structure** (approximate):
```groovy
class CachedEcrCredentials {
    String username
    String password
    Instant expiration
}
```

**Modified Structure**:
```groovy
@Canonical
class CachedEcrCredentials {
    String accessKeyId       // For re-authentication
    String secretAccessKey   // For re-authentication
    String sessionToken      // NEW - session token if using STS
    Instant stsExpiration    // NEW - when STS credentials expire
    String authToken         // ECR authorization token
    Instant tokenExpiration  // When ECR token expires

    boolean isExpiring() {
        // Check if STS credentials expire within 5 minutes
        if (stsExpiration != null) {
            return Instant.now().plus(Duration.ofMinutes(5)).isAfter(stsExpiration)
        }
        // Check if ECR token expires within 5 minutes
        return Instant.now().plus(Duration.ofMinutes(5)).isAfter(tokenExpiration)
    }
}
```

**Changes**:
- Add `sessionToken` field (String, nullable)
- Add `stsExpiration` field (Instant, nullable for static credentials)
- Add `isExpiring()` method to check if credentials need refresh
- Store temporary credentials for potential re-authentication

---

## Data Flow

### Authentication with Role-Based Credentials

```
1. Platform sends: roleArn (as username), externalId (as password)
                        ↓
2. Wave detects role ARN pattern in username
                        ↓
3. Wave calls STS AssumeRole(roleArn, externalId)
                        ↓
4. AWS STS returns: accessKeyId, secretAccessKey, sessionToken, expiration
                        ↓
5. Wave creates AwsCreds.ofKeys(accessKeyId, secretAccessKey, sessionToken, region, false)
                        ↓
6. Wave creates CachedEcrCredentials:
   - Stores STS credentials (with sessionToken and stsExpiration)
   - Calls ECR GetAuthorizationToken using session credentials
   - Stores ECR authToken and tokenExpiration
                        ↓
7. Wave returns ECR authToken to client
                        ↓
8. On next request within 55 minutes:
   - Cache hit (credentials not expiring)
   - Return cached authToken immediately
                        ↓
9. After 55 minutes (5-minute buffer before expiration):
   - isExpiring() returns true
   - Wave invalidates cache entry
   - Repeat from step 3
```

### Authentication with Static Credentials (Unchanged)

```
1. Platform sends: accessKey (as username), secretKey (as password)
                        ↓
2. Wave detects static credential pattern
                        ↓
3. Wave creates AwsCreds.ofKeys(accessKey, secretKey, null, region, false)
                        ↓
4. Wave calls ECR GetAuthorizationToken directly
                        ↓
5. Wave creates CachedEcrCredentials:
   - Stores static credentials (sessionToken = null, stsExpiration = null)
   - Stores ECR authToken and tokenExpiration
                        ↓
6. Wave returns ECR authToken to client
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

**Implementation**:
```groovy
boolean isExpiring() {
    def now = Instant.now()
    def buffer = Duration.ofMinutes(5)

    // Check STS credential expiration (for role-based auth)
    if (stsExpiration != null && now.plus(buffer).isAfter(stsExpiration)) {
        return true
    }

    // Check ECR token expiration (for both auth types)
    if (now.plus(buffer).isAfter(tokenExpiration)) {
        return true
    }

    return false
}
```

### Cache TTL Calculation

**Caffeine Cache Configuration**:
```groovy
cache = Caffeine.newBuilder()
    .expireAfter(new Expiry<String, CachedEcrCredentials>() {
        Duration expireAfterCreate(String key, CachedEcrCredentials value, long currentTime) {
            // For role-based: expire 5 minutes before STS expiration
            if (value.stsExpiration != null) {
                long ttl = Duration.between(Instant.now(), value.stsExpiration).toMillis()
                return Duration.ofMillis(ttl - Duration.ofMinutes(5).toMillis())
            }
            // For static: expire 5 minutes before ECR token expiration
            long ttl = Duration.between(Instant.now(), value.tokenExpiration).toMillis()
            return Duration.ofMillis(ttl - Duration.ofMinutes(5).toMillis())
        }
    })
    .maximumSize(10_000)
    .build()
```

---

## Memory Impact

### Per Cache Entry Size

| Field | Size | Notes |
|-------|------|-------|
| `accessKeyId` | 20 bytes | ASIA... or AKIA... format |
| `secretAccessKey` | 40 bytes | Base64-like string |
| `sessionToken` | ~1000 bytes | JWT-like token (role-based only) |
| `stsExpiration` | 16 bytes | Instant timestamp |
| `authToken` | ~1500 bytes | Base64 encoded ECR token |
| `tokenExpiration` | 16 bytes | Instant timestamp |
| **Total (role-based)** | **~2.6 KB** | |
| **Total (static)** | **~1.6 KB** | No session token |

### Scale Estimates

| Customers | Memory (Role-based) | Memory (Static) |
|-----------|---------------------|-----------------|
| 100 | 260 KB | 160 KB |
| 1,000 | 2.6 MB | 1.6 MB |
| 10,000 | 26 MB | 16 MB |

**Conclusion**: Negligible impact (Wave typically uses 2 GB heap)

---

## Backward Compatibility

### Static Credentials Keep Working

**No changes to existing flow**:
- `sessionToken` remains `null` for static credentials
- `stsExpiration` remains `null` for static credentials
- Cache key calculation handles null values: `"${sessionToken ?: ''}"`
- `isExpiring()` method falls back to checking `tokenExpiration` only

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

def cached = new CachedEcrCredentials(
    accessKeyId: 'AKIAIOSFODNN7EXAMPLE',
    secretAccessKey: 'wJalrX...',
    sessionToken: null,           // NEW field, but null
    stsExpiration: null,          // NEW field, but null
    authToken: 'AWS:encoded_token',
    tokenExpiration: Instant.now().plus(Duration.ofHours(12))
)

// isExpiring() only checks tokenExpiration when stsExpiration is null
assert !cached.isExpiring()  // Works correctly
```

---

## Summary

Wave's data model changes are **minimal and non-breaking**:

✅ **Two class modifications**:
- `AwsCreds`: Separate `roleArn`/`externalId` fields, factory methods `ofRole()`/`ofKeys()`, backward-compatible hash
- `CachedEcrCredentials`: Add `sessionToken` and `stsExpiration` fields + `isExpiring()` method

✅ **No database changes** in Wave (all persistence in Platform)

✅ **Backward compatible**: Static credentials work unchanged (nullable fields)

✅ **Low memory overhead**: ~2.6 KB per customer = ~26 MB for 10K customers

✅ **Proactive refresh**: 5-minute buffer prevents expiration failures