# Quickstart: Cross-Account IAM Role Authentication

## Prerequisites

### Required

- Java 21+ installed
- AWS account with permissions to:
  - Create IAM roles
  - Use STS AssumeRole
  - Access ECR registries
- Git

### Optional but Recommended

- Docker (for running local containers)
- AWS CLI configured (`aws configure`)
- IntelliJ IDEA or VS Code with Groovy support

---

## Setup

### 1. Clone and Build Wave

```bash
# Clone the repository
cd /path/to/wave

# Checkout the feature branch
git checkout 001-cross-account-iam-role-auth

# Build the project
./gradlew assemble

# Run tests
./gradlew test
```

**Expected Output**:
```
BUILD SUCCESSFUL in 2m 15s
```

---

### 2. Configure AWS Credentials for Wave

Wave needs AWS credentials to assume customer roles. Set up Wave's service IAM role:

**Option A: Use AWS CLI Profile**

```bash
export AWS_PROFILE=wave-dev
export AWS_REGION=us-east-1
```

**Option B: Use Environment Variables**

```bash
export AWS_ACCESS_KEY_ID=AKIA...
export AWS_SECRET_ACCESS_KEY=wJalrX...
export AWS_REGION=us-east-1
```

**Verify Configuration**:
```bash
aws sts get-caller-identity
```

**Expected Output**:
```json
{
    "UserId": "AIDA...",
    "Account": "111111111111",
    "Arn": "arn:aws:iam::111111111111:user/wave-dev"
}
```

---

### 3. Create Test IAM Role in Target AWS Account

In your **customer AWS account** (the account with ECR registries):

**Step 1: Create IAM Role**

```bash
# Create trust policy document
cat > trust-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::111111111111:root"
      },
      "Action": "sts:AssumeRole",
      "Condition": {
        "StringEquals": {
          "sts:ExternalId": "test-external-id-12345"
        }
      }
    }
  ]
}
EOF

# Create the IAM role
aws iam create-role \
  --role-name WaveEcrAccessTest \
  --assume-role-policy-document file://trust-policy.json \
  --description "Test role for Wave ECR access"
```

**Step 2: Attach ECR Permissions**

```bash
# Create permissions policy document
cat > ecr-permissions.json <<EOF
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
        "ecr:ListImages"
      ],
      "Resource": "*"
    }
  ]
}
EOF

# Attach policy to role
aws iam put-role-policy \
  --role-name WaveEcrAccessTest \
  --policy-name EcrReadAccess \
  --policy-document file://ecr-permissions.json
```

**Step 3: Note the Role ARN**

```bash
aws iam get-role --role-name WaveEcrAccessTest --query 'Role.Arn' --output text
```

**Expected Output**:
```
arn:aws:iam::222222222222:role/WaveEcrAccessTest
```

Save this ARN - you'll need it for testing!

---

### 4. Run Wave Locally

```bash
# Start Wave with development configuration
./run.sh
```

**Expected Output**:
```
Micronaut application starting...
Wave service started on port 8080
```

**Verify Wave is Running**:
```bash
curl http://localhost:8080/service-info
```

---

## Testing User Stories

### User Story 1: Basic IAM Role Authentication (P1)

**Test Objective**: Verify Wave can authenticate using IAM role instead of static credentials

**Test Steps**:

1. **Create a test ECR repository** (if you don't have one):

```bash
aws ecr create-repository --repository-name wave-test-repo --region us-east-1
```

2. **Get your ECR registry URL**:

```bash
aws sts get-caller-identity --query 'Account' --output text
# Output: 222222222222

# Registry URL format: {account-id}.dkr.ecr.{region}.amazonaws.com
# Example: 222222222222.dkr.ecr.us-east-1.amazonaws.com
```

3. **Call Wave's authentication endpoint** with role ARN:

```bash
curl -X POST http://localhost:8080/ecr/login \
  -H "Content-Type: application/json" \
  -d '{
    "registry": "222222222222.dkr.ecr.us-east-1.amazonaws.com",
    "username": "arn:aws:iam::222222222222:role/WaveEcrAccessTest",
    "password": "test-external-id-12345"
  }'
```

**Expected Response** (Success):
```json
{
  "username": "AWS",
  "password": "eyJwYXlsb2FkIjoi...",
  "expiresAt": "2024-02-09T23:00:00Z"
}
```

**Verify in Logs**:
```
DEBUG i.s.w.s.a.AwsEcrService - Detected role ARN, using STS AssumeRole authentication
INFO  i.s.w.s.a.AwsEcrService - Successfully assumed role: arn:aws:iam::222222222222:role/WaveEcrAccessTest
INFO  i.s.w.s.a.AwsEcrService - Temporary credentials expire at: 2024-02-09T12:00:00Z
```

4. **Test with Docker**:

```bash
# Login to ECR using Wave's credentials
echo "eyJwYXlsb2FkIjoi..." | docker login --username AWS --password-stdin 222222222222.dkr.ecr.us-east-1.amazonaws.com

# Pull a test image
docker pull 222222222222.dkr.ecr.us-east-1.amazonaws.com/wave-test-repo:latest
```

**Success Criteria**:
- ✅ Wave calls STS AssumeRole
- ✅ Wave returns ECR authorization token
- ✅ Docker can authenticate and pull images

---

### User Story 2: Automatic Credential Refresh (P1)

**Test Objective**: Verify Wave refreshes credentials before they expire

**Test Steps**:

1. **Modify STS session duration to 15 minutes** (for faster testing):

```groovy
// In AwsEcrService.groovy (temporary for testing)
.durationSeconds(900)  // 15 minutes instead of 3600
```

2. **Make first authentication request**:

```bash
curl -X POST http://localhost:8080/ecr/login \
  -H "Content-Type: application/json" \
  -d '{
    "registry": "222222222222.dkr.ecr.us-east-1.amazonaws.com",
    "username": "arn:aws:iam::222222222222:role/WaveEcrAccessTest",
    "password": "test-external-id-12345"
  }'
```

**Check Logs** (note expiration time):
```
INFO  i.s.w.s.a.AwsEcrService - Temporary credentials expire at: 2024-02-09T10:15:00Z
INFO  i.s.w.s.a.AwsEcrCache - Cached credentials with key: sha256:abc123...
```

3. **Make second request within 10 minutes** (should hit cache):

```bash
# Run same curl command after 5 minutes
```

**Check Logs** (should see cache hit):
```
DEBUG i.s.w.s.a.AwsEcrCache - Cache HIT for key: sha256:abc123...
DEBUG i.s.w.s.a.AwsEcrService - Using cached credentials, expires at: 2024-02-09T10:15:00Z
```

4. **Wait until 10 minutes mark** (5 minutes before expiration):

```bash
# Run same curl command after 10 minutes
```

**Check Logs** (should see refresh):
```
DEBUG i.s.w.s.a.AwsEcrCache - Credentials expiring soon (in 4m 30s), invalidating cache
INFO  i.s.w.s.a.AwsEcrService - Refreshing credentials for role: arn:aws:iam::222222222222:role/WaveEcrAccessTest
INFO  i.s.w.s.a.AwsEcrService - Successfully refreshed credentials, new expiration: 2024-02-09T10:30:00Z
```

**Success Criteria**:
- ✅ First request: Cache MISS, STS call made
- ✅ Second request (within 10 min): Cache HIT, no STS call
- ✅ Third request (after 10 min): Cache invalidated, automatic refresh
- ✅ No authentication failures due to expiration

---

### User Story 3: Backward Compatibility with Static Credentials (P1)

**Test Objective**: Verify static credentials still work unchanged

**Test Steps**:

1. **Create static AWS credentials**:

```bash
# Get your AWS access key
export AWS_ACCESS_KEY=AKIA...
export AWS_SECRET_KEY=wJalrX...
```

2. **Call Wave with static credentials**:

```bash
curl -X POST http://localhost:8080/ecr/login \
  -H "Content-Type: application/json" \
  -d '{
    "registry": "222222222222.dkr.ecr.us-east-1.amazonaws.com",
    "username": "AKIAIOSFODNN7EXAMPLE",
    "password": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
  }'
```

**Check Logs** (should NOT see STS calls):
```
DEBUG i.s.w.s.a.AwsEcrService - Detected access key ID, using static credential authentication
INFO  i.s.w.s.a.AwsEcrService - Successfully authenticated with static credentials
```

3. **Verify no STS client initialization for static credentials**:

```bash
# Monitor logs for STS-related messages
grep -i "sts" wave.log
# Should return empty or minimal results
```

**Success Criteria**:
- ✅ Static credentials still work
- ✅ No STS AssumeRole calls made
- ✅ Same performance as before (no regression)
- ✅ Existing integration tests pass

---

### User Story 4: External ID Security (P1)

**Test Objective**: Verify external ID prevents unauthorized access

**Test Steps**:

1. **Test with CORRECT external ID** (should succeed):

```bash
curl -X POST http://localhost:8080/ecr/login \
  -H "Content-Type: application/json" \
  -d '{
    "registry": "222222222222.dkr.ecr.us-east-1.amazonaws.com",
    "username": "arn:aws:iam::222222222222:role/WaveEcrAccessTest",
    "password": "test-external-id-12345"
  }'
```

**Expected**: Success (HTTP 200)

2. **Test with WRONG external ID** (should fail):

```bash
curl -X POST http://localhost:8080/ecr/login \
  -H "Content-Type: application/json" \
  -d '{
    "registry": "222222222222.dkr.ecr.us-east-1.amazonaws.com",
    "username": "arn:aws:iam::222222222222:role/WaveEcrAccessTest",
    "password": "wrong-external-id-99999"
  }'
```

**Expected Response** (HTTP 401):
```json
{
  "error": "Unauthorized",
  "message": "Wave's service role cannot assume the specified IAM role. Verify the trust policy allows Wave's service role and includes the correct external ID."
}
```

**Check Logs**:
```
ERROR i.s.w.s.a.AwsEcrService - STS AssumeRole failed: AccessDenied - User is not authorized to perform: sts:AssumeRole
```

3. **Test without external ID** (should fail):

```bash
curl -X POST http://localhost:8080/ecr/login \
  -H "Content-Type: application/json" \
  -d '{
    "registry": "222222222222.dkr.ecr.us-east-1.amazonaws.com",
    "username": "arn:aws:iam::222222222222:role/WaveEcrAccessTest",
    "password": ""
  }'
```

**Expected**: HTTP 400 (Bad Request) or HTTP 401 (Unauthorized)

**Success Criteria**:
- ✅ Correct external ID: Authentication succeeds
- ✅ Wrong external ID: Authentication fails with clear error message
- ✅ Missing external ID: Authentication fails
- ✅ Security: AWS blocks unauthorized access attempts

---

### User Story 5: Comprehensive Error Handling (P2)

**Test Objective**: Verify clear error messages for common failures

**Test Cases**:

#### 5a. Invalid Role ARN Format

```bash
curl -X POST http://localhost:8080/ecr/login \
  -H "Content-Type: application/json" \
  -d '{
    "registry": "222222222222.dkr.ecr.us-east-1.amazonaws.com",
    "username": "invalid-arn-format",
    "password": "test-external-id-12345"
  }'
```

**Expected Response** (HTTP 400):
```json
{
  "error": "Bad Request",
  "message": "Invalid IAM role ARN format: invalid-arn-format"
}
```

#### 5b. Role Doesn't Exist

```bash
curl -X POST http://localhost:8080/ecr/login \
  -H "Content-Type: application/json" \
  -d '{
    "registry": "222222222222.dkr.ecr.us-east-1.amazonaws.com",
    "username": "arn:aws:iam::222222222222:role/NonExistentRole",
    "password": "test-external-id-12345"
  }'
```

**Expected Response** (HTTP 401):
```json
{
  "error": "Unauthorized",
  "message": "Wave's service role cannot assume the specified IAM role. Verify the trust policy allows Wave's service role and includes the correct external ID."
}
```

#### 5c. Missing Trust Policy

```bash
# Create role WITHOUT trust policy for Wave
aws iam create-role \
  --role-name WaveEcrAccessNoTrust \
  --assume-role-policy-document '{"Version":"2012-10-17","Statement":[]}'

# Try to use it
curl -X POST http://localhost:8080/ecr/login \
  -H "Content-Type: application/json" \
  -d '{
    "registry": "222222222222.dkr.ecr.us-east-1.amazonaws.com",
    "username": "arn:aws:iam::222222222222:role/WaveEcrAccessNoTrust",
    "password": "test-external-id-12345"
  }'
```

**Expected**: AccessDenied error with clear message

**Success Criteria**:
- ✅ All error cases return appropriate HTTP status codes
- ✅ Error messages are actionable and specific
- ✅ No sensitive information leaked in error messages

---

### User Story 6: Credential Caching for Performance (P2)

**Test Objective**: Verify credentials are cached efficiently

**Test Steps**:

1. **Enable metrics/logging for cache hits**:

Add this to `application-local.yml`:
```yaml
logging:
  level:
    io.seqera.wave.service.aws.cache: DEBUG
```

2. **Make 10 consecutive requests**:

```bash
for i in {1..10}; do
  echo "Request $i:"
  curl -X POST http://localhost:8080/ecr/login \
    -H "Content-Type: application/json" \
    -d '{
      "registry": "222222222222.dkr.ecr.us-east-1.amazonaws.com",
      "username": "arn:aws:iam::222222222222:role/WaveEcrAccessTest",
      "password": "test-external-id-12345"
    }'
  sleep 2
done
```

3. **Analyze cache metrics**:

```bash
# Count STS calls in logs
grep "AssumeRole" wave.log | wc -l
# Expected: 1 (only first request)

# Count cache hits
grep "Cache HIT" wave.log | wc -l
# Expected: 9 (requests 2-10)

# Calculate cache hit rate
echo "Cache hit rate: 9/10 = 90%"
```

4. **Measure latency difference**:

```bash
# First request (cache miss)
time curl -X POST http://localhost:8080/ecr/login ...
# Expected: ~500-1000ms (includes STS call)

# Second request (cache hit)
time curl -X POST http://localhost:8080/ecr/login ...
# Expected: ~50-100ms (no STS call)
```

**Success Criteria**:
- ✅ Cache hit rate > 90% (9 out of 10 requests)
- ✅ Cache hit latency < 100ms
- ✅ Cache miss latency < 1000ms
- ✅ Only 1 STS call for 10 identical requests

---

## Troubleshooting

### Issue: "STS AssumeRole failed: AccessDenied"

**Possible Causes**:
1. Wave's service role not in customer's IAM trust policy
2. Wrong external ID
3. IAM role doesn't exist

**Solution**:
```bash
# Verify trust policy
aws iam get-role --role-name WaveEcrAccessTest --query 'Role.AssumeRolePolicyDocument'

# Verify external ID in trust policy matches
# Check Principal ARN matches Wave's service role
```

---

### Issue: "Credentials already expired"

**Possible Causes**:
1. System clock skew
2. STS credentials not refreshing

**Solution**:
```bash
# Check system time
date
ntpq -p

# Check credential expiration in logs
grep "expire" wave.log | tail -5

# Force cache invalidation
curl -X DELETE http://localhost:8080/cache/invalidate
```

---

### Issue: "Cache hit rate is low (<80%)"

**Possible Causes**:
1. Frequent credential refreshes
2. Cache eviction due to memory pressure
3. Different cache keys for same credentials

**Solution**:
```bash
# Check cache configuration
grep "cache" src/main/resources/application.yml

# Monitor cache statistics
curl http://localhost:8080/metrics | grep cache

# Check cache key generation
grep "stableHash" wave.log
```

---

## Next Steps

After completing the quickstart:

1. **Run full test suite**:
   ```bash
   ./gradlew test
   ```

2. **Review test coverage**:
   ```bash
   ./gradlew jacocoTestReport
   open build/reports/jacoco/test/html/index.html
   ```

3. **Test integration with Platform** (if available):
   - Configure Platform to use role-based credentials
   - Trigger container pull through Platform
   - Verify end-to-end flow

4. **Performance testing**:
   - Load test with concurrent requests
   - Verify cache hit rate > 95% under load
   - Measure p95 latency < 200ms (cache hit)

---

## References

- [AWS IAM Roles Documentation](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles.html)
- [AWS STS AssumeRole API](https://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRole.html)
- [ECR Authentication](https://docs.aws.amazon.com/AmazonECR/latest/userguide/registry_auth.html)
- Wave CLAUDE.md: Development commands and architecture