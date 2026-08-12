---
title: Configure Wave
description: Prepare a self-hosted Wave deployment for production and configure optional features.
---

Configure a self-hosted Wave deployment for production and add optional features. Complete the production checklist before you serve traffic. Configure optional features such as email notifications and build caching as needed.

:::info
See the [Configuration reference](reference.md) for the full list of configuration options for self-hosted Wave deployments.
:::

## Production checklist

A freshly installed Wave service boots and returns `200` on `/service-info`, but it is not yet production-ready. Complete this checklist after [verifying your installation](post-install.md) and before serving production traffic. For the underlying options, see the [Configuration reference](reference.md).

### Require authentication

By default Wave allows anonymous pulls. In production, require authentication so only paired Platform clients can request containers:

```yaml
wave:
  capabilities:
    anonymous-access: false
```

With anonymous access disabled, every request must carry a valid Platform-issued token.

For a regulated deployment that must also stop Wave from serving images directly, brokering registry credentials, or exposing its HTML pages, add `strict` to `MICRONAUT_ENVIRONMENTS` instead. That disables all four capability toggles at once. See [Capabilities](reference.md#capabilities).

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

For the full list of limits, see [Rate limits](reference.md#rate-limits) in the Configuration reference.

### Configure cleanup and retention

Builds and augmented images accumulate. Set cleanup and retention so storage stays bounded. See `wave.cleanup.*` in the [Configuration reference](reference.md). Also set a retention or lifecycle policy on your build and cache registries.

### Right-size resources

Reserve about 2 GB memory and 0.2 CPU per Wave instance, with limits of 4 GB and 1 CPU, matching the sizing in the install paths. Run multiple replicas behind the load balancer for availability.

Set `WAVE_JVM_OPTS` to match the container limit. The image defaults to an 850 MB heap whatever the limit says, so a 4 GB container leaves most of its memory unused until you override it. Setting the variable replaces the whole default option set rather than adding to it, so copy the defaults from `src/main/jib/launch.sh` and adjust `-Xmx`.

Size the build node pool and cap concurrency with `wave.job-manager.max-running-jobs` and a build-namespace `ResourceQuota`.

### Restrict build-pod egress

Build pods run user-supplied Dockerfiles. On a build-enabled deployment, apply a `NetworkPolicy` to the build namespace that limits egress to your registries and S3.

### Review security headers

Wave sends HTTP security headers (HSTS, frame options, content-type options, referrer policy, permissions policy, and a content security policy) by default. Review them against your environment and adjust the content security policy if you front Wave with additional origins. See [Security headers](reference.md#security-headers) in the Configuration reference.

## Email notifications

Wave sends email notifications for build-related events. Configure delivery through SMTP or Amazon Simple Email Service (SES).

### SMTP

Append `mail` to the `MICRONAUT_ENVIRONMENTS` value your install path already sets, then configure the SMTP settings in your Wave configuration:

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

For every `mail.*` setting, see [Email configuration](reference.md#email-configuration).

### SES

In AWS environments, Wave integrates directly with SES using Identity and Access Management (IAM) authentication instead of SMTP credentials. Wave uses the AWS SDK to send emails through the SES API.

:::info[**Prerequisites**]

You need the following:

- SES configured in the same AWS region as your Wave deployment.
- An IAM role or user with `ses:SendEmail` and `ses:SendRawEmail` permissions.
- A verified sending domain or address for your `from` email address.
- SES out of sandbox mode if you send to unverified addresses.

:::

Append both `mail` and `aws-ses` to the `MICRONAUT_ENVIRONMENTS` value your install path already sets, then set the sender address in your Wave configuration:

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

Wave scans container builds for vulnerabilities. This feature requires the build service and additional scanning infrastructure.

:::info[**Prerequisites**]

You need the following:

- The Wave build service enabled (`wave.build.enabled: true`).
- Compute resources for scan jobs, which run on the build infrastructure.
- An S3 bucket path for scan reports.

:::

Enable scanning in your Wave configuration and set the report location:

```yaml
wave:
  build:
    enabled: true
  scan:
    enabled: true
    reports:
      path: "s3://<s3-bucket>/wave/scan-reports"
```

Wave runs scans with its bundled Trivy-based scanner image. Override the image with `wave.scan.image.name` if you mirror it to your own registry. For all scan options, see [Container scan process](reference.md#container-scan-process).

## Build layer cache

`wave.build.cache` takes either a container repository or an S3 path. [Enable Wave builds](aws-build.md) sets it to the ECR repository created there, which is the default choice on AWS; add a [lifecycle policy](https://docs.aws.amazon.com/AmazonECR/latest/userguide/LifecyclePolicies.html) expiring untagged images to keep its storage bounded.

To use S3 as the BuildKit cache backend instead, point `wave.build.cache` at a bucket path:

```yaml
wave:
  build:
    cache: "s3://wave-cache-bucket/buildkit"
    cache-bucket-region: "us-east-1"     # Optional if AWS_REGION is set
    cache-bucket-upload-parallelism: 8   # Optional, controls parallel S3 uploads
```

S3 cache needs no static credentials. Build pods pick up the AWS identity of their node or service account, so extend the IRSA policy from [Enable Wave builds](aws-build.md#grant-wave-access-to-aws-apis-with-irsa) with `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject`, `s3:ListBucket`, `s3:AbortMultipartUpload`, `s3:ListMultipartUploadParts`, and `s3:ListBucketMultipartUploads` on the cache path. For the full set of build cache settings, see [Container build process](reference.md#container-build-process).

## Client IP address resolution

Wave uses client IP addresses for rate limiting. By default, Wave reads the socket address, which clients cannot spoof.

For AWS ALB deployments, append `alb` to the `MICRONAUT_ENVIRONMENTS` value your install path already sets. The `alb` profile trusts the `X-Forwarded-For` header from the ALB to resolve the client IP.

:::warning
Enable the `alb` profile only when Wave runs behind a trusted ALB. If Wave is exposed directly to the internet, trusting `X-Forwarded-For` lets clients spoof their IP address and bypass rate limiting.
:::
