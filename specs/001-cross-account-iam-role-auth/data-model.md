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
    String accessKey
    String secretKey
    String sessionToken    // NEW - null for static credentials
    String region
    boolean ecrPublic

    String stableHash() {
        Hashing.sha256()
            .hashString(
                "$accessKey:$secretKey:${sessionToken ?: ''}:$region:$ecrPublic",
                Charsets.UTF_8
            )
            .toString()
    }
}
```

**Changes**:
- Add `sessionToken` field (String, nullable)
- Update `stableHash()` to include session token in cache key
- Session token must be part of cache key to distinguish different STS sessions

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
5. Wave creates AwsCreds(accessKeyId, secretAccessKey, sessionToken, region, false)
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
3. Wave creates AwsCreds(accessKey, secretKey, null, region, false)
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

### Why Session Token Matters

**Problem**: Two different STS sessions can have same accessKeyId but different sessionToken

**Example**:
```groovy
// Session 1 (assume role at 10:00 AM)
creds1 = AwsCreds('ASIA...', 'secretKey1', 'FwoGZXIv...sessionToken1', 'us-east-1', false)
hash1 = SHA256('ASIA...:secretKey1:FwoGZXIv...sessionToken1:us-east-1:false')

// Session 2 (assume role at 11:00 AM, same role)
creds2 = AwsCreds('ASIA...', 'secretKey2', 'FwoGZXIv...sessionToken2', 'us-east-1', false)
hash2 = SHA256('ASIA...:secretKey2:FwoGZXIv...sessionToken2:us-east-1:false')

// Different sessions → different cache entries
assert hash1 != hash2
```

**Without session token in hash**: Cache collision → wrong credentials returned
**With session token in hash**: Unique cache entry per STS session ✅

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
def staticCreds = new AwsCreds(
    accessKey: 'AKIAIOSFODNN7EXAMPLE',
    secretKey: 'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY',
    sessionToken: null,  // NEW field, but null for static
    region: 'us-east-1',
    ecrPublic: false
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
- `AwsCreds`: Add nullable `sessionToken` field
- `CachedEcrCredentials`: Add `sessionToken` and `stsExpiration` fields + `isExpiring()` method

✅ **No database changes** in Wave (all persistence in Platform)

✅ **Backward compatible**: Static credentials work unchanged (nullable fields)

✅ **Low memory overhead**: ~2.6 KB per customer = ~26 MB for 10K customers

✅ **Proactive refresh**: 5-minute buffer prevents expiration failures