---
title: Configuration
description: Harden a self-hosted Wave deployment for production and configure optional features.
---

Configure a self-hosted Wave deployment for production and add optional features. Harden every deployment with the checklist below before you serve traffic. Configure optional features such as email notifications and build caching as needed.

:::info
See [Reference](reference.md) for the full list of configuration options for self-hosted Wave deployments.
:::

## Harden for production

A freshly installed Wave service boots and returns `200` on `/service-info`, but it is not yet production-ready. Apply this hardening checklist after [verifying your installation](post-install.md) and before serving production traffic. For the underlying options, see [Reference](reference.md).

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

:::warning
Rate limiting is active only when the `rate-limit` entry is present in the `MICRONAUT_ENVIRONMENTS` variable of your deployment. The install guides include it. Without it, `rate-limit.*` settings are silently ignored.
:::

For the full list of limits, see [Rate limits](reference.md#rate-limits) in the Reference.

### Configure cleanup and retention

Builds and augmented images accumulate. Set cleanup and retention so storage stays bounded. See `wave.cleanup.*` in [Reference](reference.md). Also set a retention or lifecycle policy on your build and cache registries.

### Right-size resources

Reserve about 2 GB memory and 0.2 CPU per Wave instance, with limits of 4 GB and 1 CPU, matching the sizing in the install paths. Run multiple replicas behind the load balancer for availability.

Size the build node pool and cap concurrency with `wave.job-manager.max-running-jobs` and a build-namespace `ResourceQuota`.

### Review security headers

Wave sends HTTP security headers (HSTS, frame options, content-type options, referrer policy, permissions policy, and a content security policy) by default. Review them against your environment and adjust the content security policy if you front Wave with additional origins. See [Security headers](reference.md#security-headers) in the Reference.

## Email notifications

Wave sends email notifications for build-related events. Configure delivery through SMTP or Amazon Simple Email Service (SES).

### SMTP

Add `mail` to your Micronaut environments:

```yaml
# Add 'mail' to your existing environments
MICRONAUT_ENVIRONMENTS: "postgres,redis,lite,mail"
```

Configure the SMTP settings in your Wave configuration:

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

In AWS environments, Wave integrates directly with Amazon Simple Email Service (SES) using Identity and Access Management (IAM) authentication instead of SMTP credentials. Wave uses the AWS SDK to send emails through the SES API. No SMTP configuration is needed.

:::info[**Prerequisites**]

You need the following:

- SES configured in the same AWS region as your Wave deployment.
- An IAM role or user with `ses:SendEmail` and `ses:SendRawEmail` permissions.
- A verified sending domain or address for your `from` email address.
- SES out of sandbox mode if you send to unverified addresses.

:::

Add `aws-ses` to your Micronaut environments along with `mail`:

```yaml
# Add both 'mail' and 'aws-ses' to your existing environments
MICRONAUT_ENVIRONMENTS: "postgres,redis,lite,mail,aws-ses"
```

Set the sender address in your Wave configuration:

```yaml
mail:
  from: "wave-notifications@your-domain.com"
```

Grant Wave the following IAM permissions:

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

Wave uses SES in the AWS region where it runs. Verify your sending domain in the AWS SES console and set sending limits for your expected volume.

## Security scanning

Wave performs security scanning on container builds. This feature requires the build service and additional scanning infrastructure.

:::info[**Prerequisites**]

You need the following:

- The Wave build service enabled (`wave.build.enabled: true`).
- A configured, reachable scanning backend.
- Compute resources for scanning workloads.

:::

```
wave:
  build:
    enabled: true
  scan:
    enabled: true
```

## ECR cache repository

Use Amazon Elastic Container Registry (ECR) as a cache repository to store and reuse build layers. Reusing cached layers speeds up builds and reduces bandwidth. ECR cache requires the Wave build service and works only in AWS deployments with ECR access.

:::info[**Prerequisites**]

You need the following:

- An ECR repository in the same AWS region as Wave.
- IAM permissions for Wave to push to and pull from ECR.
- An ECR repository reachable from the Wave build infrastructure.

:::

Configure the ECR cache repository in your Wave configuration:

```yaml
wave:
  build:
    enabled: true
    cache: "123456789012.dkr.ecr.us-east-1.amazonaws.com/wave-cache"
```

[Enable Wave builds](aws-build.md) defines the ECR cache IAM permissions. Add your cache ARN as an allowed `Resource`.

To create and configure the ECR cache repository:

1. Create the ECR repository:

    ```bash
    aws ecr create-repository --repository-name wave-cache --region us-east-1
    ```

2. Configure a lifecycle policy to manage cache storage costs:
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

The `wave.build.cache` setting takes a cache repository URL or S3 path:

| Setting                    | Description                       | Example                                                   |
| -------------------------- | --------------------------------- | --------------------------------------------------------- |
| `wave.build.cache`         | Cache repository URL or S3 path   | `123456789012.dkr.ecr.us-east-1.amazonaws.com/wave-cache` |

## S3 cache authentication

When you set `wave.build.cache` to an S3 bucket path, Wave uses S3 as the BuildKit cache backend. Wave authenticates with native AWS mechanisms instead of static credentials in configuration files.

For the related configuration options (`wave.build.cache`, `wave.build.cache-bucket-region`, `wave.build.cache-bucket-upload-parallelism`), see [Container build process](reference.md#container-build-process).

### Kubernetes deployments

S3 cache uses IAM Roles for Service Accounts (IRSA) for credential-free authentication.

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

For Docker-based builds, use an EC2 instance profile for automatic credential management.

Attach an IAM role with the S3 permissions shown earlier to the EC2 instance running Docker. BuildKit uses the instance metadata service to obtain temporary credentials. The AWS SDK in BuildKit discovers and uses the instance profile credentials, so no further configuration is required.

:::note
For development and testing only, you can provide AWS credentials through environment variables:

```bash
export AWS_ACCESS_KEY_ID=your_access_key
export AWS_SECRET_ACCESS_KEY=your_secret_key
export AWS_REGION=us-east-1
```

**Warning:** Do not use this approach in production. It requires managing static credentials. Use an EC2 instance profile for production Docker deployments.
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

Wave uses client IP addresses for rate limiting. By default, Wave reads the socket address, which clients cannot spoof.

For AWS ALB deployments, enable the `alb` profile:

```bash
export MICRONAUT_ENVIRONMENTS=alb
```

The `alb` profile trusts the `X-Forwarded-For` header from the ALB to resolve the client IP.
