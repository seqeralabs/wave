---
title: Configure for production
description: Harden a verified Wave installation for production traffic.
---

A freshly installed Wave service boots and returns `200` on `/service-info`, but it is not yet production-ready. Apply this hardening checklist after [verifying your installation](post-install.md) and before serving production traffic.

Work through each item. Each links to [Configure Wave](../configure-wave.md) for the property detail.

## Require authentication

By default Wave allows anonymous pulls (`wave.allowAnonymous: true`). In production, require authentication so only paired Platform clients can request containers:

```yaml
wave:
  allowAnonymous: false
```

With anonymous access disabled, every request must carry a valid Platform-issued token.

## Terminate TLS

Wave does not terminate TLS itself. Front it with an ingress or load balancer that holds the certificate. For example, an Application Load Balancer (ALB) with an AWS Certificate Manager (ACM) certificate matching the Wave hostname, and a Route 53 alias record pointing at the load balancer. Confirm `wave.server.url` uses the `https://` hostname clients reach.

:::note
Private CA and self-signed certificate handling is not yet documented. If your registries or Platform use a private CA, contact Seqera support.
:::

## Set rate limits

Wave rate-limits container pulls. Tune the limits for your expected load so anonymous or runaway clients cannot exhaust the service:

```yaml
rate-limit:
  pull:
    anonymous: 250/1h
    authenticated: 2000/1m
  timeout-errors:
    max-rate: 100/1m
```

## Configure cleanup and retention

Builds and augmented images accumulate. Set cleanup and retention so storage stays bounded. See `wave.cleanup.*` in [Configure Wave](../configure-wave.md). For the full Wave configuration, also set a retention or lifecycle policy on your build and cache registries.

## Right-size resources

Reserve about 2 GB memory and 0.2 CPU per Wave instance, with limits of 4 GB and 1 CPU, matching the sizing in the install paths. Run multiple replicas behind the load balancer for availability.

For the full Wave configuration, also size the build node pool and cap concurrency with `wave.job-manager.max-running-jobs` and a build-namespace `ResourceQuota`.

## Review security headers

Wave sends HTTP security headers (HSTS, frame options, content-type options, referrer policy, permissions policy, and a content security policy) by default. Review them against your environment and adjust the content security policy if you front Wave with additional origins. See `wave.security.http-headers.*` in [Configure Wave](../configure-wave.md).
