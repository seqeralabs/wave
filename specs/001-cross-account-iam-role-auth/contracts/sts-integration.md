# STS Integration Contract

## Overview

This document defines the contract between Wave and AWS STS (Security Token Service) for assuming IAM roles to obtain temporary credentials, including jump role chaining.

## STS AssumeRole Request

### API Endpoint

```
POST https://sts.<region>.amazonaws.com/
Action: AssumeRole
Version: 2011-06-15
```

### Request Parameters

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `RoleArn` | String | Yes | ARN of the IAM role to assume | `arn:aws:iam::123456789012:role/WaveEcrAccess` |
| `RoleSessionName` | String | Yes | Identifier for the session (appears in CloudTrail) | `wave-ecr-123456789012-1707494400000` |
| `ExternalId` | String | No | Unique identifier to prevent confused deputy attacks (conditionally included when provided) | `a1b2c3d4-e5f6-7890-abcd-ef1234567890` |
| `DurationSeconds` | Integer | Yes | Session duration in seconds (900 to 43200) | `3600` (1 hour) |
| `Policy` | String | No | Session policy (not used) | - |
| `Tags` | List | No | Session tags (not used) | - |

### Groovy/Java SDK Request

```groovy
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest

// Built via AwsEcrService.buildAssumeRoleRequest()
AssumeRoleRequest request = AssumeRoleRequest.builder()
    .roleArn('arn:aws:iam::123456789012:role/WaveEcrAccess')
    .roleSessionName("wave-ecr-${extractAccountId(roleArn)}-${System.currentTimeMillis()}")
    .durationSeconds(3600)
    .externalId('a1b2c3d4-e5f6-7890-abcd-ef1234567890')  // only set when provided
    .build()
```

**Session Name Format**:
- Target role: `wave-ecr-{accountId}-{timestamp}` (e.g., `wave-ecr-123456789012-1707494400000`)
- Jump role: `wave-jump-{accountId}-{timestamp}` (e.g., `wave-jump-128997144437-1707494400000`)
- Account ID extracted via `extractAccountId(roleArn)` — returns `unknown` if ARN cannot be parsed

---

## STS AssumeRole Response

### Success Response (HTTP 200)

```groovy
AssumeRoleResponse {
    credentials: Credentials {
        accessKeyId: "ASIATEMP...",           // Temporary access key (starts with ASIA)
        secretAccessKey: "wJalrX...",         // Temporary secret key
        sessionToken: "FwoGZXIv...",          // Session token (required for API calls)
        expiration: "2024-02-09T12:00:00Z"    // When credentials expire (Instant)
    },
    assumedRoleUser: AssumedRoleUser {
        assumedRoleId: "AROA...:wave-ecr-123456789012-1707494400000",
        arn: "arn:aws:sts::123456789012:assumed-role/WaveEcrAccess/wave-ecr-123456789012-1707494400000"
    },
    packedPolicySize: 6  // Percentage of max policy size used
}
```

### Groovy Extraction

```groovy
Credentials creds = response.credentials()

String accessKeyId = creds.accessKeyId()
String secretAccessKey = creds.secretAccessKey()
String sessionToken = creds.sessionToken()
Instant expiration = creds.expiration()
```

---

## Error Responses

### AccessDenied (HTTP 403)

**Cause**: Wave's service role (or jump role) is not allowed to assume the target role

```xml
<ErrorResponse>
  <Error>
    <Type>Sender</Type>
    <Code>AccessDenied</Code>
    <Message>User: arn:aws:iam::WAVE_ACCOUNT:role/WaveServiceRole is not authorized to perform: sts:AssumeRole on resource: arn:aws:iam::123456789012:role/WaveEcrAccess</Message>
  </Error>
</ErrorResponse>
```

**Resolution**: Customer must add Wave's service role (or jump role) to their IAM role's trust policy

**Wave Handling** (`AwsEcrService.mapStsException()`):
```groovy
case 'AccessDenied':
    return new AwsEcrAuthException(
        "Wave's service role does not have permission to assume the specified role. " +
        "Verify the trust policy in your IAM role allows Wave to assume it. " +
        "Error: ${e.message}", e)
```

---

### InvalidParameterValue (HTTP 400)

**Cause**: Invalid external ID, role ARN, or session name format

```xml
<ErrorResponse>
  <Error>
    <Type>Sender</Type>
    <Code>InvalidParameterValue</Code>
    <Message>ExternalId must satisfy regular expression pattern: [\w+=,.@:\/-]*</Message>
  </Error>
</ErrorResponse>
```

**Resolution**: Validate inputs — Wave delegates validation to AWS STS

**Wave Handling**:
```groovy
case 'InvalidParameterValue':
    return new AwsEcrAuthException(
        "Invalid role ARN or external ID format. " +
        "Ensure the role ARN follows the pattern 'arn:aws:iam::ACCOUNT_ID:role/ROLE_NAME'. " +
        "Error: ${e.message}", e)
```

---

### RegionDisabledException (HTTP 403)

**Cause**: STS is disabled in the specified region

```xml
<ErrorResponse>
  <Error>
    <Type>Sender</Type>
    <Code>RegionDisabledException</Code>
    <Message>STS is not activated in this region for account: 123456789012</Message>
  </Error>
</ErrorResponse>
```

**Resolution**: Customer must enable STS endpoints for the region

**Wave Handling**:
```groovy
case 'RegionDisabledException':
    return new AwsEcrAuthException(
        "STS is not enabled in the specified region. " +
        "Enable STS endpoints for this region in AWS. " +
        "Error: ${e.message}", e)
```

---

### ExpiredTokenException (HTTP 400)

**Cause**: Attempting to use expired temporary credentials (e.g., expired jump role credentials)

```xml
<ErrorResponse>
  <Error>
    <Type>Sender</Type>
    <Code>ExpiredTokenException</Code>
    <Message>The security token included in the request is expired</Message>
  </Error>
</ErrorResponse>
```

**Resolution**: Automatically refresh credentials and retry

**Wave Handling** — Two layers:
1. **`mapStsException()`**: Maps to `AwsEcrAuthException` with "Temporary credentials have expired" message
2. **`assumeRole()` retry**: When jump role credentials expire (race between cache TTL and actual expiry), catches `ExpiredTokenException` and retries once via `doAssumeJumpRole()` (bypasses cache for fresh credentials)

```groovy
// In assumeRole() — jump role expired token retry (FR-011)
catch (StsException e) {
    if (e.awsErrorDetails()?.errorCode() == 'ExpiredTokenException') {
        final freshCreds = doAssumeJumpRole(region)
        return stsClient(region, freshCreds).withCloseable { freshClient ->
            assumeTargetRole(freshClient, roleArn, externalId)
        }
    }
    throw e
}
```

---

## Jump Role Chaining

### Overview

When `wave.aws.jump-role-arn` is configured, Wave performs two-hop role assumption:

```
Wave's default credentials
    → STS AssumeRole (jump role)
        → jump role temporary credentials
            → STS AssumeRole (customer's target role)
                → target role temporary credentials
                    → ECR GetAuthorizationToken
```

### Jump Role Cache

Jump role credentials are cached in `AwsRoleCache` (keyed by region) to avoid redundant STS calls:

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

### Configuration

```yaml
wave:
  aws:
    jump-role-arn: "arn:aws:iam::WAVE_ACCOUNT_ID:role/WaveJumpRole"      # optional
    jump-external-id: "jump-role-external-id"                             # optional
    jump-role-cache:
      duration: 45m    # max cache duration for jump role credentials
      max-size: 100    # max cached entries (one per region)
```

---

## Security Requirements

### Customer IAM Role Trust Policy

**Required Configuration** (customer must configure this):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::WAVE_ACCOUNT_ID:role/WaveServiceRole"
      },
      "Action": "sts:AssumeRole",
      "Condition": {
        "StringEquals": {
          "sts:ExternalId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
        }
      }
    }
  ]
}
```

**Note**: When jump role chaining is configured, the Principal should be the jump role ARN instead of Wave's direct identity.

**Critical**: External ID MUST match exactly, or AssumeRole will fail with `AccessDenied`

### Customer IAM Role Permissions Policy

**Required ECR Permissions** (customer must configure this):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "ecr:DescribeRepositories",
        "ecr:ListImages",
        "ecr:DescribeImages"
      ],
      "Resource": "*"
    }
  ]
}
```

---

## Wave Service Role Requirements

**Wave's IAM Role Policy** (Wave deployment must have this):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "sts:AssumeRole",
      "Resource": "arn:aws:iam::*:role/*",
      "Condition": {
        "StringEquals": {
          "sts:ExternalId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
        }
      }
    }
  ]
}
```

**Note**: In production, restrict `Resource` to specific customer role patterns

---

## Performance Characteristics

### Latency

| Percentile | Latency | Notes |
|------------|---------|-------|
| p50 | ~200ms | Typical AssumeRole call |
| p95 | ~500ms | Includes network variability |
| p99 | ~1000ms | May include retries |

### Throttling Limits

| Limit Type | Value | Notes |
|------------|-------|-------|
| STS API TPS | 5000 | Per AWS account |
| Burst capacity | 10000 | Short-term burst |

**Wave's Expected Usage**:
- 1000 customers x 1 call/hour = **0.28 calls/second**
- **Utilization**: 0.0056% of limit (no throttling concern)

---

## Retry Strategy

### Retryable Errors

- HTTP 5xx server errors
- `Throttling` error code

### Non-Retryable Errors

- `AccessDenied` (customer configuration issue)
- `InvalidParameterValue` (invalid input)
- `RegionDisabledException` (region configuration issue)

### Retry Configuration

Uses `Retryable.of(stsConfig)` with `StsClientConfig` implementing `Retryable.Config`:

```groovy
// StsClientConfig bean — wave.aws.sts.retry.* properties
delay: 1s
maxDelay: 10s
attempts: 3
multiplier: 2.0
jitter: 0.25
```

**Implementation** (`AwsEcrService.doAssumeRole()`):
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

**Retry Schedule** (typical):
- Attempt 1: Immediate
- Attempt 2: +1s delay (jittered)
- Attempt 3: +2s delay (jittered)
- Total max: ~3s of retries

---

## Monitoring & Observability

### Recommended Metrics (MO-001 through MO-005)

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `wave.sts.assume_role.calls` | Counter | result={success\|failure}, error_code | Total AssumeRole calls |
| `wave.sts.assume_role.duration` | Timer | - | AssumeRole call latency |
| `wave.ecr.auth.cache.hit_rate` | Gauge | - | Cache hit rate |
| `wave.ecr.auth.cache.size` | Gauge | - | Number of cached entries |
| `wave.ecr.auth.method` | Counter | type={static\|role} | Auth method usage |

### CloudTrail Logging

Every AssumeRole call generates a CloudTrail event in the **target account**:

```json
{
  "eventName": "AssumeRole",
  "eventSource": "sts.amazonaws.com",
  "userIdentity": {
    "type": "AssumedRole",
    "principalId": "AROA...:WaveServiceRole",
    "arn": "arn:aws:iam::WAVE_ACCOUNT:role/WaveServiceRole"
  },
  "requestParameters": {
    "roleArn": "arn:aws:iam::123456789012:role/WaveEcrAccess",
    "roleSessionName": "wave-ecr-123456789012-1707494400000",
    "externalId": "HIDDEN_FOR_SECURITY"
  },
  "responseElements": {
    "credentials": {
      "expiration": "2024-02-09T12:00:00Z"
    }
  }
}
```

**Note**: External ID is hidden in CloudTrail for security

---

## References

- [AWS STS AssumeRole API Documentation](https://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRole.html)
- [AWS SDK for Java 2.x - STS](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/sts/package-summary.html)
- [IAM Roles External ID Best Practices](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_create_for-user_externalid.html)