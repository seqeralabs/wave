---
title: Configure Wave
---

This page describes common operations to set up and configure Wave.

:::info
See [Configuration reference](./configuration.md) for a full list configuration options for self-hosted Wave deployments.
:::

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
    scan: true
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
        cache:
            enabled: true
            repository: "123456789012.dkr.ecr.us-east-1.amazonaws.com/wave-cache"
```

#### IAM permissions

Wave requires the following IAM permissions for ECR cache operations:

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
                "ecr:PutImage",
                "ecr:InitiateLayerUpload",
                "ecr:UploadLayerPart",
                "ecr:CompleteLayerUpload"
            ],
            "Resource": ["arn:aws:ecr:us-east-1:123456789012:repository/wave-cache"]
        },
        {
            "Effect": "Allow",
            "Action": ["ecr:GetAuthorizationToken"],
            "Resource": "*"
        }
    ]
}
```

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

| Setting            | Description          | Example                                                   |
| ------------------ | -------------------- | --------------------------------------------------------- |
| `cache.enabled`    | Enable build caching | `true`                                                    |
| `cache.repository` | ECR repository URL   | `123456789012.dkr.ecr.us-east-1.amazonaws.com/wave-cache` |

**Note:** ECR cache requires Wave build service to be enabled and is only available in AWS deployments with proper ECR access configured.
