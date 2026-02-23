# Feature Specification: Cross-Account IAM Role Authentication for AWS ECR

**Feature Branch**: `cross-account-iam-role-authentication`
**Created**: 2026-02-09
**Status**: Draft
**Input**: User description: "Enable Wave to authenticate to customer AWS ECR registries using AWS STS AssumeRole with IAM roles instead of static credentials for more secure and auditable cross-account access"

**Deployment Scope**: Available for both Seqera Platform Cloud and Platform Enterprise (self-hosted) deployments.

**Affected Repositories**:
- `nf-tower-cloud` (Platform)
- `wave` (This repository)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Basic IAM Role Authentication (Priority: P1)

A Platform administrator wants to configure Wave to access their company's AWS ECR registry using IAM role-based authentication instead of long-lived AWS access keys, providing better security and compliance with their organization's security policies.

**Why this priority**: This is the core MVP functionality. Without this, the feature provides no value. It enables the fundamental security improvement that customers need.

**Independent Test**: Can be fully tested by configuring a role ARN in Platform credentials, attempting to pull a container image from a customer ECR registry, and verifying Wave successfully assumes the role and authenticates. Delivers immediate security value by eliminating static credentials.

**Acceptance Scenarios**:

1. **Given** a customer has an IAM role configured with ECR permissions and a trust policy allowing Wave's service role, **When** they enter the role ARN in Platform's credential configuration, **Then** Platform auto-generates and displays a unique external ID
2. **Given** Platform credentials are configured with a role ARN and external ID, **When** Wave attempts to authenticate to the customer's ECR registry, **Then** Wave detects the role ARN pattern and uses STS AssumeRole instead of static credentials
3. **Given** Wave successfully assumes the customer's IAM role, **When** Wave retrieves temporary credentials from STS, **Then** Wave uses these credentials (including session token) to authenticate with ECR
4. **Given** a container image pull request to an ECR registry with role-based credentials, **When** the authentication succeeds, **Then** the image is successfully pulled and cached by Wave

---

### User Story 2 - Automatic Credential Refresh (Priority: P1)

Wave automatically manages temporary credential lifecycle, refreshing credentials before they expire to ensure uninterrupted service without manual intervention.

**Why this priority**: Critical for production reliability. Without automatic refresh, temporary credentials would expire during operation causing authentication failures. This is essential for the feature to be production-ready.

**Independent Test**: Can be tested by configuring role-based authentication, monitoring credential expiration times, and verifying Wave proactively refreshes credentials 5 minutes before expiration. Delivers continuous availability without manual intervention.

**Acceptance Scenarios**:

1. **Given** Wave has obtained temporary credentials with a 1-hour expiration, **When** the credentials are within 5 minutes of expiration, **Then** Wave automatically refreshes them by making a new AssumeRole call
2. **Given** credentials are cached in Wave's credential cache, **When** Wave checks the cache, **Then** expired or expiring credentials are automatically invalidated and refreshed
3. **Given** a long-running operation (e.g., large image pull), **When** credentials expire during the operation, **Then** Wave retries with fresh credentials without failing the operation
4. **Given** Wave maintains multiple customer credential sessions, **When** any session approaches expiration, **Then** only that specific session's credentials are refreshed (no impact on other sessions)

---

### User Story 3 - Backward Compatibility with Static Credentials (Priority: P1)

Existing Wave deployments with static AWS credentials continue to work without any configuration changes or service disruption when the new role-based authentication feature is deployed.

**Why this priority**: Essential for production rollout. Breaking existing customers is unacceptable. This ensures zero-downtime deployment and allows gradual migration.

**Independent Test**: Can be tested by deploying the new code to an environment with existing static credentials configured, pulling images, and verifying all operations continue to work identically. Delivers confidence for production deployment.

**Acceptance Scenarios**:

1. **Given** Platform credentials configured with AWS access key ID and secret access key (static credentials), **When** Wave authenticates to ECR, **Then** Wave uses the existing static credential flow without attempting STS AssumeRole
2. **Given** a mixed environment with some registries using static credentials and others using role-based authentication, **When** Wave handles requests for both types, **Then** both authentication methods work correctly based on credential format
3. **Given** the new Wave version is deployed, **When** existing container pull operations are in progress, **Then** no operations fail due to the deployment
4. **Given** no changes to existing Platform credential configurations, **When** the new version is deployed, **Then** all existing functionality continues to work identically

---

### User Story 4 - External ID Security (Priority: P1)

Platform automatically generates a unique external ID for each workspace when configuring IAM role-based credentials, preventing confused deputy attacks and ensuring secure cross-account access.

**Why this priority**: Core security requirement. Without external ID, the IAM role assumption is vulnerable to security attacks. This is a non-negotiable security control.

**Independent Test**: Can be tested by creating multiple workspace credentials, verifying each receives a unique UUID external ID, and confirming Wave includes this external ID in AssumeRole calls. Delivers fundamental security protection.

**Acceptance Scenarios**:

1. **Given** a Platform administrator creates new AWS credentials with a role ARN, **When** the credentials are saved, **Then** Platform automatically generates a unique UUID as the external ID
2. **Given** an external ID has been generated for a workspace, **When** the administrator views the credential configuration, **Then** the external ID is displayed in a read-only field with copy-to-clipboard functionality
3. **Given** Wave receives authentication request with role ARN and external ID, **When** Wave calls STS AssumeRole, **Then** the external ID is included in the AssumeRole request
4. **Given** a customer's IAM role trust policy requires the correct external ID, **When** Wave attempts to assume the role with the correct external ID, **Then** the role assumption succeeds
5. **Given** an attacker attempts to use Wave's service role to access a customer's resources without the correct external ID, **When** the AssumeRole call is made, **Then** AWS denies the request due to external ID mismatch

---

### User Story 5 - Comprehensive Error Handling (Priority: P2)

Wave provides clear, actionable error messages when IAM role assumption fails, helping administrators quickly diagnose and resolve configuration issues.

**Why this priority**: Important for operational excellence and reducing support burden. While not blocking core functionality, good error messages significantly improve user experience and reduce setup time.

**Independent Test**: Can be tested by intentionally misconfiguring various aspects (wrong role ARN, missing trust policy, invalid external ID) and verifying Wave returns specific, actionable error messages. Delivers better troubleshooting experience.

**Acceptance Scenarios**:

1. **Given** a customer's IAM role trust policy doesn't include Wave's service role, **When** Wave attempts to assume the role, **Then** Wave returns error: "Wave's service role does not have permission to assume the specified role. Verify the trust policy in your IAM role."
2. **Given** an invalid external ID is provided, **When** Wave attempts to assume the role, **Then** Wave returns error: "Invalid external ID format. External ID must be 2-1224 characters and match pattern [\w+=,.@:\/-]*"
3. **Given** STS is not enabled in the customer's AWS region, **When** Wave attempts to assume the role, **Then** Wave returns error: "STS is not enabled in the specified region. Enable STS endpoints for this region in AWS."
4. **Given** temporary credentials have expired during an operation, **When** the operation is retried, **Then** Wave logs "Temporary credentials have expired. Retrying with fresh credentials." and automatically retries
5. **Given** any STS error occurs, **When** the error is logged, **Then** sufficient context is provided (role ARN, region, error code) for troubleshooting

---

### User Story 6 - Credential Caching for Performance (Priority: P2)

Wave efficiently caches temporary credentials to minimize STS API calls while ensuring credentials remain valid, optimizing both performance and cost.

**Why this priority**: Important for production performance and AWS cost optimization. While the feature works without optimization, efficient caching significantly improves response time and reduces STS API costs.

**Independent Test**: Can be tested by making multiple authentication requests with the same role ARN, monitoring STS API calls, and verifying only one AssumeRole call is made per hour (or when credentials expire). Delivers better performance and cost efficiency.

**Acceptance Scenarios**:

1. **Given** Wave successfully assumes a role and obtains temporary credentials, **When** the credentials are stored in cache, **Then** the cache key includes access key, secret key, session token, and region to ensure uniqueness
2. **Given** cached credentials exist for a role ARN, **When** a new authentication request arrives for the same role, **Then** Wave returns cached credentials without making a new STS API call
3. **Given** multiple concurrent requests for the same ECR registry, **When** credentials are not cached, **Then** only one STS AssumeRole call is made (subsequent requests wait for the first call to complete)
4. **Given** temporary credentials with 1-hour expiration, **When** credentials are cached, **Then** the cache hit rate exceeds 95% for typical workloads
5. **Given** credentials are approaching expiration (within 5 minutes), **When** Wave checks the cache, **Then** the credentials are treated as expired and refreshed proactively

---

### User Story 7 - Platform UI Integration (Priority: P2)

Platform provides intuitive UI for administrators to configure IAM role-based authentication, including auto-generation and display of the external ID required for the IAM trust policy.

**Why this priority**: Important for user experience and adoption. While technically possible to configure without UI changes, good UX is essential for customer adoption and reduces configuration errors.

**Independent Test**: Can be tested by accessing Platform's credential configuration UI, entering a role ARN, and verifying the external ID is auto-generated and displayed with copy functionality. Delivers easy setup experience.

**Acceptance Scenarios**:

1. **Given** an administrator accesses AWS credential configuration in Platform, **When** they select IAM role-based authentication, **Then** the UI displays fields for role ARN and auto-generates an external ID
2. **Given** an external ID has been generated, **When** the administrator views it, **Then** the external ID is displayed in a read-only field with a copy-to-clipboard button
3. **Given** an administrator is configuring role-based authentication for the first time, **When** they view the UI, **Then** helpful tooltips or documentation links explain the role ARN format and external ID purpose
4. **Given** a role ARN is entered in the UI, **When** the administrator saves the configuration, **Then** Platform validates the ARN format before accepting it
5. **Given** existing static credentials are configured, **When** the administrator switches to role-based authentication, **Then** the UI clearly indicates the difference and potential implications

---

### User Story 8 - Multi-Region Support (Priority: P3)

Wave correctly handles IAM role authentication across different AWS regions, allowing customers with multi-region ECR registries to use role-based authentication regardless of region.

**Why this priority**: Nice-to-have enhancement. Most customers use a single region or the default STS endpoint works globally. This provides better optimization for specific edge cases.

**Independent Test**: Can be tested by configuring role-based authentication for ECR registries in different AWS regions (us-east-1, eu-west-1, ap-southeast-1) and verifying all authenticate successfully. Delivers global deployment flexibility.

**Acceptance Scenarios**:

1. **Given** a customer has ECR registries in multiple AWS regions, **When** Wave authenticates to registries in different regions, **Then** each authentication uses the appropriate regional STS endpoint
2. **Given** a role assumption for a specific region, **When** Wave makes the AssumeRole call, **Then** the temporary credentials are valid for that region's ECR registry
3. **Given** STS global endpoint is disabled in a customer's AWS account, **When** Wave uses regional endpoints, **Then** authentication continues to work correctly

---

### Edge Cases

- **What happens when STS API is temporarily unavailable?**
  - Wave should retry with exponential backoff
  - If all retries fail, return clear error message indicating STS service issue
  - Cached credentials (if still valid) continue to work during STS outages

- **How does the system handle role ARN format variations?**
  - Validate role ARN matches pattern: `^arn:aws:iam::\d{12}:role/.+`
  - Reject malformed ARNs with clear error message
  - Handle ARNs with path components (e.g., `arn:aws:iam::123456789012:role/service-role/MyRole`)

- **What happens when temporary credentials expire during a large image pull?**
  - Wave detects ExpiredTokenException
  - Automatically obtains fresh credentials
  - Retries the failed operation transparently
  - Log the refresh event for monitoring

- **How does Wave handle credential cache invalidation during deployment?**
  - Graceful shutdown: Allow in-flight operations to complete
  - Cache persistence: Consider persisting cache across restarts (optional)
  - Cache warming: Re-authenticate on startup if needed

- **What happens when customer revokes Wave's access (removes trust policy)?**
  - Next AssumeRole call fails with AccessDenied
  - Wave returns clear error message to user
  - Cached credentials continue to work until expiration
  - After expiration, all operations fail with clear error message

- **How does Wave distinguish between static credentials and role ARNs in existing configurations?**
  - Check if username field matches role ARN pattern
  - If match: Use STS AssumeRole flow
  - If no match: Use static credential flow
  - No ambiguity or false positives

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST detect when a username matches IAM role ARN pattern (`^arn:aws:iam::\d{12}:role/.+`) and automatically use STS AssumeRole authentication
- **FR-002**: System MUST support both static credentials (backward compatible) and role-based authentication simultaneously in the same deployment
- **FR-003**: Platform MUST auto-generate a unique UUID as external ID when administrator configures IAM role-based credentials
- **FR-004**: Wave MUST include the external ID in all STS AssumeRole API calls when authenticating with role-based credentials
- **FR-005**: Wave MUST request temporary credentials with 1-hour (3600 second) session duration from STS
- **FR-006**: Wave MUST store temporary credentials including access key ID, secret access key, session token, and expiration timestamp
- **FR-007**: Wave MUST include session token in all ECR API calls when using temporary credentials
- **FR-008**: Wave MUST cache temporary credentials to minimize STS API calls, achieving >95% cache hit rate
- **FR-009**: Wave MUST automatically refresh temporary credentials 5 minutes before expiration
- **FR-010**: Wave MUST invalidate cached credentials that are expired or within 5 minutes of expiration
- **FR-011**: Wave MUST retry failed operations with fresh credentials when receiving ExpiredTokenException
- **FR-012**: System MUST provide specific error messages for common STS failures: AccessDenied, InvalidParameterException, RegionDisabledException
- **FR-013**: Platform MUST validate role ARN format before saving credentials
- **FR-014**: Platform MUST validate external ID format (2-1224 characters, pattern `[\w+=,.@:\/-]*`)
- **FR-015**: Platform MUST display external ID in read-only field with copy-to-clipboard functionality
- **FR-016**: External ID MUST remain immutable after generation (no automatic rotation)
- **FR-017**: Cache key MUST include access key, secret key, session token, region, and ECR public flag to ensure uniqueness
- **FR-018**: System MUST support credential configurations where some use static credentials and others use role-based authentication
- **FR-019**: Wave MUST use session name format `wave-ecr-access-{timestamp}` or similar for AssumeRole calls
- **FR-020**: System MUST log all STS AssumeRole calls with sufficient context (role ARN, region, external ID presence, success/failure)

### Non-Functional Requirements

- **NFR-001**: STS AssumeRole success rate MUST exceed 99.9% (excluding customer configuration errors)
- **NFR-002**: Credential cache hit rate MUST exceed 95% for typical workloads
- **NFR-003**: Credential refresh MUST complete within 2 seconds under normal conditions
- **NFR-004**: System MUST handle at least 1000 concurrent credential sessions without performance degradation
- **NFR-005**: Deployment of new version MUST NOT break existing static credential configurations (zero-downtime deployment)
- **NFR-006**: Temporary credentials MUST NOT be logged or exposed in plaintext in application logs
- **NFR-007**: External ID generation MUST use cryptographically secure random UUID (UUID v4)
- **NFR-008**: System MUST provide audit trail for all role assumption attempts via AWS CloudTrail

### Key Entities

- **AwsSecurityKeys (Platform)**: Represents AWS credentials configuration in Platform
  - Key attributes: `registry`, `username` (access key ID **or** IAM role ARN), `password` (secret key **or** external ID when username is a role ARN)
  - Note: Role ARN is detected at the Wave service layer via pattern matching (`^arn:aws:iam::\d{12}:role/.+`). No separate `assumeRoleArn` field is needed — the existing `username`/`password` fields are reused for backward compatibility.
  - Relationships: Associated with workspace/organization in Platform

- **AwsCreds (Wave)**: Represents AWS credentials used by Wave for cache key computation
  - Key attributes: `accessKey`, `secretKey`, `sessionToken` (nullable — null for static credentials), `region`, `ecrPublic`
  - For role-based auth: cache key uses `roleArn` as `accessKey` and `externalId` as `secretKey` (with null `sessionToken`) to ensure stable cache keys across credential refreshes
  - For static auth: cache key uses actual access key and secret key (with null `sessionToken`)
  - Relationships: Used as tiered cache key via `stableHash()`, temporary lifecycle for role-based credentials

- **AWS STS Credentials (transient)**: Temporary credentials obtained from STS AssumeRole
  - Key attributes: `accessKeyId`, `secretAccessKey`, `sessionToken`, `expiration`
  - Note: These are transient — not stored as a separate entity. They are used immediately to call ECR and the resulting auth token is cached with a TTL derived from `expiration` minus 5-minute buffer.
  - Relationships: Returned by STS, used to construct ECR API calls

- **AssumeRoleRequest**: STS API request for assuming IAM role
  - Key attributes: `roleArn`, `externalId`, `roleSessionName`, `durationSeconds`
  - Relationships: Sent to AWS STS service, returns temporary credentials

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: STS AssumeRole authentication success rate exceeds 99.9% for correctly configured roles
- **SC-002**: Average credential cache hit rate exceeds 95% across all deployments
- **SC-003**: Zero backward compatibility breaks - 100% of existing static credential configurations continue to work after deployment
- **SC-004**: Credential refresh operations complete within 2 seconds (p95)
- **SC-005**: Customer setup time for IAM role-based authentication is under 15 minutes with provided documentation
- **SC-006**: Support tickets related to AWS credential issues reduce by at least 30% after feature adoption
- **SC-007**: Zero security incidents related to confused deputy attacks (external ID working correctly)
- **SC-008**: Average STS API calls per customer per hour is less than 2 (indicating effective caching)
- **SC-009**: 90% of customers successfully configure role-based authentication on first attempt (measured via error rates)
- **SC-010**: System maintains 99.9% uptime during credential refresh operations (no authentication failures due to expiration)
- **SC-011**: Production deployment completes with zero rollbacks due to compatibility issues
- **SC-012**: Customer audit requirements are met - 100% of role assumptions visible in AWS CloudTrail

### Monitoring & Observability

- **MO-001**: Metrics tracked for STS AssumeRole calls (success/failure rates, latency, error types)
- **MO-002**: Metrics tracked for credential cache (hit rate, size, eviction count)
- **MO-003**: Metrics tracked for credential refresh operations (frequency, latency, failure rate)
- **MO-004**: Alerts configured for: STS failure rate >1%, cache hit rate <90%, credential expiration without refresh
- **MO-005**: Dashboards showing: authentication method breakdown (static vs role), regional distribution, error trends