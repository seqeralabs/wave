# STS Integration Contract

## Overview

This document defines the contract between Wave and AWS STS (Security Token Service) for assuming IAM roles to obtain temporary credentials.

## STS AssumeRole Request

### API Endpoint

```
POST https://sts.amazonaws.com/
Action: AssumeRole
Version: 2011-06-15
```

### Request Parameters

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `RoleArn` | String | Yes | ARN of the IAM role to assume | `arn:aws:iam::123456789012:role/WaveEcrAccess` |
| `RoleSessionName` | String | Yes | Identifier for the session (appears in CloudTrail) | `wave-ecr-access-1707494400000` |
| `ExternalId` | String | Yes | Unique identifier to prevent confused deputy attacks | `a1b2c3d4-e5f6-7890-abcd-ef1234567890` |
| `DurationSeconds` | Integer | Yes | Session duration in seconds (900 to 43200) | `3600` (1 hour) |
| `Policy` | String | No | Session policy (not used in initial implementation) | - |
| `Tags` | List | No | Session tags (not used in initial implementation) | - |

### Groovy/Java SDK Request

```groovy
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse

AssumeRoleRequest request = AssumeRoleRequest.builder()
    .roleArn('arn:aws:iam::123456789012:role/WaveEcrAccess')
    .roleSessionName("wave-ecr-access-${System.currentTimeMillis()}")
    .externalId('a1b2c3d4-e5f6-7890-abcd-ef1234567890')
    .durationSeconds(3600)
    .build()

AssumeRoleResponse response = stsClient.assumeRole(request)
```

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
        assumedRoleId: "AROA...:wave-ecr-access-1707494400000",
        arn: "arn:aws:sts::123456789012:assumed-role/WaveEcrAccess/wave-ecr-access-1707494400000"
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

log.info("Assumed role successfully. Credentials expire at: ${expiration}")
```

---

## Error Responses

### AccessDenied (HTTP 403)

**Cause**: Wave's service role is not allowed to assume the target role

```xml
<ErrorResponse>
  <Error>
    <Type>Sender</Type>
    <Code>AccessDenied</Code>
    <Message>User: arn:aws:iam::WAVE_ACCOUNT:role/WaveServiceRole is not authorized to perform: sts:AssumeRole on resource: arn:aws:iam::123456789012:role/WaveEcrAccess</Message>
  </Error>
</ErrorResponse>
```

**Resolution**: Customer must add Wave's service role to their IAM role's trust policy

**Groovy Exception**:
```groovy
catch (StsException e) {
    if (e.awsErrorDetails().errorCode() == 'AccessDenied') {
        throw new UnauthorizedException(
            "Wave's service role does not have permission to assume the specified role. " +
            "Verify the trust policy in your IAM role.",
            e
        )
    }
}
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

**Resolution**: Validate inputs before calling STS

**Groovy Exception**:
```groovy
catch (StsException e) {
    if (e.awsErrorDetails().errorCode() == 'InvalidParameterValue') {
        throw new BadRequestException(
            "Invalid external ID or role ARN format: ${e.awsErrorDetails().errorMessage()}",
            e
        )
    }
}
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

**Groovy Exception**:
```groovy
catch (StsException e) {
    if (e.awsErrorDetails().errorCode() == 'RegionDisabledException') {
        throw new BadRequestException(
            "STS is not enabled in the specified region. " +
            "Enable STS endpoints for this region in AWS.",
            e
        )
    }
}
```

---

### ExpiredToken (HTTP 400)

**Cause**: Attempting to use credentials after expiration

```xml
<ErrorResponse>
  <Error>
    <Type>Sender</Type>
    <Code>ExpiredToken</Code>
    <Message>The security token included in the request is expired</Message>
  </Error>
</ErrorResponse>
```

**Resolution**: Automatically refresh credentials and retry

**Groovy Exception**:
```groovy
catch (AwsServiceException e) {
    if (e.awsErrorDetails().errorCode() == 'ExpiredToken') {
        log.warn("Temporary credentials expired. Refreshing...")
        cache.invalidate(credKey)
        return authenticateWithRetry(registry, roleArn, externalId)  // Retry
    }
}
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
- 1000 customers × 1 call/hour = **0.28 calls/second**
- **Utilization**: 0.0056% of limit (no throttling concern)

---

## Contract Validation

### Pre-Request Validation (Wave)

```groovy
void validateAssumeRoleInputs(String roleArn, String externalId) {
    // Validate role ARN format
    if (!roleArn?.matches(/^arn:aws:iam::\d{12}:role\/.+/)) {
        throw new BadRequestException("Invalid IAM role ARN format: ${roleArn}")
    }

    // Validate external ID format
    if (externalId == null || externalId.length() < 2 || externalId.length() > 1224) {
        throw new BadRequestException("External ID must be 2-1224 characters")
    }

    if (!externalId.matches(/[\w+=,.@:\/-]*/)) {
        throw new BadRequestException("External ID contains invalid characters")
    }
}
```

### Post-Response Validation (Wave)

```groovy
void validateAssumeRoleResponse(AssumeRoleResponse response) {
    Credentials creds = response.credentials()

    // Validate credentials are present
    if (creds == null || creds.accessKeyId() == null ||
        creds.secretAccessKey() == null || creds.sessionToken() == null) {
        throw new WaveException("STS returned incomplete credentials")
    }

    // Validate expiration is in the future
    if (creds.expiration().isBefore(Instant.now())) {
        throw new WaveException("STS returned already-expired credentials")
    }

    // Validate temporary access key format (starts with ASIA)
    if (!creds.accessKeyId().startsWith('ASIA')) {
        log.warn("Unexpected access key format: ${creds.accessKeyId().take(4)}...")
    }
}
```

---

## Retry Strategy

### Retryable Errors

- `ServiceUnavailableException` (HTTP 503)
- `RequestTimeout` (HTTP 408)
- Network connectivity errors

### Retry Configuration

```groovy
@Retryable(
    attempts = '3',
    delay = '500ms',
    multiplier = '2.0',
    includes = [StsException],
    excludes = [AccessDeniedException, InvalidParameterException]
)
Mono<AssumeRoleResponse> assumeRoleWithRetry(AssumeRoleRequest request) {
    return Mono.fromCallable(() -> stsClient.assumeRole(request))
}
```

**Retry Schedule**:
- Attempt 1: Immediate
- Attempt 2: +500ms delay
- Attempt 3: +1000ms delay
- Total max: 1500ms of retries

---

## Monitoring & Observability

### Metrics to Track

```groovy
@Timed(value = "wave.sts.assume_role", description = "STS AssumeRole duration")
@Counted(value = "wave.sts.assume_role.calls", extraTags = ["result", "success"])
AssumeRoleResponse assumeRole(AssumeRoleRequest request) {
    // ...
}
```

### Recommended Metrics

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `wave.sts.assume_role.calls` | Counter | result={success\|failure}, error_code | Total AssumeRole calls |
| `wave.sts.assume_role.duration` | Timer | - | AssumeRole call latency |
| `wave.sts.credentials.expiring_soon` | Gauge | - | # credentials expiring in <5 min |

### CloudTrail Logging

Every AssumeRole call generates a CloudTrail event in **customer's AWS account**:

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
    "roleSessionName": "wave-ecr-access-1707494400000",
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