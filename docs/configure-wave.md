---
title: Configuration
description: Harden a self-hosted Wave deployment for production and configure optional features.
---

This page describes how to configure a self-hosted Wave deployment. Harden every deployment for production using the checklist below before you serve traffic. Configure optional features such as email notifications and build caching as needed.

:::info
See [Configuration reference](./configuration.md) for the full list of configuration options for self-hosted Wave deployments.
:::

## Harden for production

A freshly installed Wave service boots and returns `200` on `/service-info`, but it is not yet production-ready. Apply this hardening checklist after [verifying your installation](install/post-install.md) and before serving production traffic. For the underlying options, see [Configuration reference](./configuration.md).

### Require authentication

By default Wave allows anonymous pulls (`wave.allowAnonymous: true`). In production, require authentication so only paired Platform clients can request containers:

```yaml
wave:
  allowAnonymous: false
```

With anonymous access disabled, every request must carry a valid Platform-issued token.

### Terminate TLS

Wave does not terminate TLS itself. Front it with an ingress or load balancer that holds the certificate. For example, an Application Load Balancer (ALB) with an AWS Certificate Manager (ACM) certificate matching the Wave hostname, and a Route 53 alias record pointing at the load balancer. Confirm `wave.server.url` uses the `https://` hostname clients reach.

:::note
Private CA and self-signed certificate handling is not yet documented. If your registries or Platform use a private CA, contact Seqera support.
:::

### Set rate limits

Wave rate-limits container pulls. Tune the limits for your expected load so anonymous or runaway clients cannot exhaust the service:

```yaml
rate-limit:
  pull:
    anonymous: 250/1h
    authenticated: 2000/1m
  timeout-errors:
    max-rate: 100/1m
```

### Configure cleanup and retention

Builds and augmented images accumulate. Set cleanup and retention so storage stays bounded. See `wave.cleanup.*` in [Configuration reference](./configuration.md). For the full Wave configuration, also set a retention or lifecycle policy on your build and cache registries.

### Right-size resources

Reserve about 2 GB memory and 0.2 CPU per Wave instance, with limits of 4 GB and 1 CPU, matching the sizing in the install paths. Run multiple replicas behind the load balancer for availability.

For the full Wave configuration, also size the build node pool and cap concurrency with `wave.job-manager.max-running-jobs` and a build-namespace `ResourceQuota`.

### Review security headers

Wave sends HTTP security headers (HSTS, frame options, content-type options, referrer policy, permissions policy, and a content security policy) by default. Review them against your environment and adjust the content security policy if you front Wave with additional origins.

## Email notifications

Wave can be configured to send email notifications for various build related events.

### SMTP

#### Configuration

Add `mail` to your Micronaut environments and configure the SMTP settings in your Wave configuration:

**Environment configuration:**

```yaml
# Add 'mail' to your existing environments
MICRONAUT_ENVIRONMENTS: "postgres,redis,lite,mail"
```

**SMTP configuration:**

```yaml
mail:
  from: "wave-notifications@your-domain.com"
  smtp:
    host: "smtp.your-provider.com"
    port: "587"
    user: "your-smtp-username"
    password: "your-smtp-password"
    auth: true
    starttls:
      enable: true
      required: true
    ssl:
      protocols: "TLSv1.2"
```

#### Configuration Options

| Setting             | Description                          | Example Values                              |
| ------------------- | ------------------------------------ | ------------------------------------------- |
| `from`              | Email address that appears as sender | `wave@company.com`                          |
| `host`              | SMTP server hostname                 | `smtp.gmail.com`, `smtp.office365.com`      |
| `port`              | SMTP server port                     | `587` (STARTTLS), `465` (SSL), `25` (plain) |
| `user`              | SMTP authentication username         | Usually your email address                  |
| `password`          | SMTP authentication password         | App password or account password            |
| `auth`              | Enable SMTP authentication           | `true` (recommended)                        |
| `starttls.enable`   | Enable STARTTLS encryption           | `true` (recommended)                        |
| `starttls.required` | Require STARTTLS encryption          | `true` (recommended)                        |
| `ssl.protocols`     | Supported SSL/TLS protocols          | `TLSv1.2`, `TLSv1.3`                        |

### SES

For AWS environments, Wave supports direct integration with Amazon Simple Email Service (SES) using IAM authentication instead of SMTP credentials.

#### Requirements

- AWS SES must be configured in the same region as your Wave deployment
- Wave must have appropriate IAM permissions to send emails via SES
- The IAM role or user must have `ses:SendEmail` and `ses:SendRawEmail` permissions

#### Configuration

Add `aws-ses` to your Micronaut environments along with `mail`:

**Environment configuration:**

```yaml
# Add both 'mail' and 'aws-ses' to your existing environments
MICRONAUT_ENVIRONMENTS: "postgres,redis,lite,mail,aws-ses"
```

**SES configuration:**

```yaml
mail:
  from: "wave-notifications@your-domain.com"
```

#### IAM permissions

Wave requires the following IAM permissions for SES integration:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": ["ses:SendEmail", "ses:SendRawEmail"],
            "Resource": "*"
        }
    ]
}
```

#### SES setup requirements

Before configuring Wave with SES:

1. **Verify your sending domain** in the AWS SES console
2. **Move out of SES sandbox** if sending to unverified email addresses
3. **Configure appropriate sending limits** for your use case
4. **Ensure SES is available** in your Wave deployment region

#### Regional considerations

Wave will automatically use SES in the same AWS region where it's deployed. Ensure SES is:

- Available and configured in your deployment region
- Has verified domains/addresses for your `from` email address
- Not in sandbox mode if sending to external recipients

**Note:** No SMTP configuration is needed when using SES with IAM authentication - Wave will use the AWS SDK to send emails directly through the SES API.

## Security scanning

Wave can perform security scanning on container builds. This feature requires the build service to be enabled and additional scanning infrastructure.

**Prerequisites:**

- Wave build service must be enabled (`wave.build.enabled: true`)
- Scanning backend must be configured and accessible
- Appropriate compute resources for scanning workloads

```
wave:
  build:
    enabled: true
  scan:
    enabled: true
```

## ECR cache repository

Wave supports using Amazon Elastic Container Registry (ECR) as a cache repository to store and reuse **build** layers, improving build performance and reducing bandwidth usage.

**Prerequisites:**

- AWS ECR repository configured in the same region as Wave
- Wave must have appropriate IAM permissions to push/pull from ECR
- ECR repository must be accessible from Wave build infrastructure

#### Configuration

Configure ECR cache repository in your Wave configuration:

```yaml
wave:
  build:
    enabled: true
    cache: "123456789012.dkr.ecr.us-east-1.amazonaws.com/wave-cache"
```

#### IAM permissions

ECR cache IAM permissions are defined in [Enable Wave builds](install/aws-build.md).  Ensure your cache ARN is an allowed `Resource`.

#### ECR repository setup

Create and configure your ECR cache repository:

1. **Create ECR repository:**

    ```bash
    aws ecr create-repository --repository-name wave-cache --region us-east-1
    ```

2. **Configure lifecycle policy** to manage cache storage costs:
    ```json
    {
        "rules": [
            {
                "rulePriority": 1,
                "selection": {
                    "tagStatus": "untagged",
                    "countType": "sinceImagePushed",
                    "countUnit": "days",
                    "countNumber": 7
                },
                "action": {
                    "type": "expire"
                }
            }
        ]
    }
    ```

#### Benefits

Using ECR as a cache repository provides:

- **Faster builds** by reusing cached layers
- **Reduced bandwidth** usage for repeated builds
- **Cost optimization** through efficient layer storage
- **Regional performance** with ECR in the same region as Wave
- **Integrated security** with AWS IAM and ECR security features

#### Configuration Options

| Setting                    | Description                       | Example                                                   |
| -------------------------- | --------------------------------- | --------------------------------------------------------- |
| `wave.build.cache`         | Cache repository URL or S3 path   | `123456789012.dkr.ecr.us-east-1.amazonaws.com/wave-cache` |

**Note:** ECR cache requires Wave build service to be enabled and is only available in AWS deployments with proper ECR access configured.

## S3 cache authentication

When using S3 as the BuildKit cache backend (by configuring `wave.build.cache` with an S3 bucket path), Wave relies on AWS native authentication mechanisms rather than static credentials in configuration files.

For the related configuration options (`wave.build.cache`, `wave.build.cache-bucket-region`, `wave.build.cache-bucket-upload-parallelism`), see [Container build process](./configuration.md#container-build-process).

### Kubernetes deployments

S3 cache uses **IAM Roles for Service Accounts (IRSA)** for secure, credential-free authentication.

Configure your Kubernetes ServiceAccount with an IAM role annotation:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: wave-build-sa
  namespace: wave-build
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::ACCOUNT_ID:role/WaveBuildRole
```

The IAM role must have permissions to access the S3 cache bucket:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject",
        "s3:ListBucket",
        "s3:AbortMultipartUpload",
        "s3:ListMultipartUploadParts",
        "s3:ListBucketMultipartUploads"
      ],
      "Resource": [
        "arn:aws:s3:::my-bucket/wave/cache",
        "arn:aws:s3:::my-bucket/wave/cache/*"
      ]
    }
  ]
}
```

Update your Wave deployment to use the annotated ServiceAccount:

```yaml
spec:
  template:
    spec:
      serviceAccountName: wave-build-sa
```

### Docker deployments

For Docker-based builds, use **EC2 Instance Profile** for automatic credential management.

Attach an IAM role to the EC2 instance running Docker with the S3 permissions shown above. BuildKit automatically uses the instance metadata service to obtain temporary credentials.

No additional configuration is required. The AWS SDK in BuildKit automatically discovers and uses the instance profile credentials.

:::note
For development and testing purposes only, you can provide AWS credentials via environment variables:

```bash
export AWS_ACCESS_KEY_ID=your_access_key
export AWS_SECRET_ACCESS_KEY=your_secret_key
export AWS_REGION=us-east-1
```

**Warning:** This approach is not recommended for production environments as it requires managing static credentials. Always use EC2 Instance Profile for production Docker deployments.
:::

### Configuration example

```yaml
wave:
  build:
    cache: "s3://wave-cache-bucket/buildkit"
    cache-bucket-region: "us-east-1"  # Optional if AWS_REGION is set
    cache-bucket-upload-parallelism: 8  # Optional, controls parallel S3 uploads
```

## Client IP address resolution

Wave uses client IP addresses for rate limiting. By default, Wave uses socket addresses (secure).

For AWS ALB deployments, enable the `alb` profile:

```bash
export MICRONAUT_ENVIRONMENTS=alb
```

This trusts X-Forwarded-For headers from ALB for correct client IP resolution.
