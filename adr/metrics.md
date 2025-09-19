---
title: Usage metrics
description: Overview of Wave's Redis-based usage metrics storage and tracking system
date: 2025-09-17
tags: [redis, metrics, keys, wave]
---

Wave can store usage metrics for specific dates and organizations in Redis.

:::note
To use metrics, enable `wave.metrics.enabled` in your Wave configuration file. See [Configuration reference](./configuration.md) for more information about Wave configuration files.
:::

## Keys

When Wave makes a keys request:

1. Wave increments the key with the current date. For example, `builds/d/2024-04-23`.
1. **For authenticated requests**: If the request includes a Seqera Platform token, Wave also performs organization-specific tracking:
    1. Wave extracts the domain from the user's email address using the access token to query Seqera Platform. For example, it extracts `seqera.io` from `user@seqera.io`.
    1. Wave increments the key with the organization-specific keys. For example, `builds/o/seqera.io` and `builds/o/seqera.io/d/2024-04-23`.

### Builds

When you make a container build request, Wave increments the following `builds` keys:

- `builds/d/<YYYY-MM-DD>`
- `builds/o/<ORG>`
- `builds/o/<ORG>/d/<YYYY-MM-DD>`

### Pulls

When you make a container build request, Wave increments the following keys:

1. Wave tracks the container image pulls using `io.seqera.wave.filter.PullMetricsRequestsFilter`.
1. Wave checks if the `Content-Type` header contains one of the following manifest values:
   - `application/vnd.docker.distribution.manifest.v2+json`
   - `application/vnd.oci.image.manifest.v1+json`
   - `application/vnd.docker.distribution.manifest.v1+prettyjws`
   - `application/vnd.docker.distribution.manifest.v1+json`
1. Wave increments the following `pulls` keys:
    - `pulls/d/<YYYY-MM-DD>`
    - `pulls/o/<ORG>`
    - `pulls/o/<ORG>/d/<YYYY-MM-DD>`
1. **For Fusion-enabled containers**: If the pulled container uses Fusion, Wave increments the following `fusion` keys:
    - `fusion/d/<YYYY-MM-DD>`
    - `fusion/o/<ORG>`
    - `fusion/o/<ORG>/d/<YYYY-MM-DD>`

## Keys reference

Wave stores usage metrics in Redis using the following key patterns:

- `pulls/d/<YYYY-MM-DD>`
- `pulls/o/<ORG>`
- `pulls/o/<ORG>/d/<YYYY-MM-DD>`
- `fusion/d/<YYYY-MM-DD>`
- `fusion/o/<ORG>`
- `fusion/o/<ORG>/d/<YYYY-MM-DD>`
- `builds/d/<YYYY-MM-DD>`
- `builds/o/<ORG>`
- `builds/o/<ORG>/d/<YYYY-MM-DD>`
- `pulls/a/<ARCH>/d/<YYYY-MM-DD>`
- `pulls/o/<ORG>/a/<ARCH>`
- `pulls/o/<ORG>/a/<ARCH>/d/<YYYY-MM-DD>`
- `pulls/a/<ARCH>`
- `fusion/a/<ARCH>/d/<YYYY-MM-DD>`
- `fusion/o/<ORG>/a/<ARCH>`
- `fusion/o/<ORG>/a/<ARCH>/d/<YYYY-MM-DD>`
- `fusion/a/<ARCH>`
- `builds/a/<ARCH>/d/<YYYY-MM-DD>`
- `builds/o/<ORG>/a/<ARCH>`
- `builds/o/<ORG>/a/<ARCH>/d/<YYYY-MM-DD>`
- `builds/a/<ARCH>`
- `mirrors/a/<ARCH>/d/<YYYY-MM-DD>`
- `mirrors/o/<ORG>/a/<ARCH>`
- `mirrors/o/<ORG>/a/<ARCH>/d/<YYYY-MM-DD>`
- `mirrors/a/<ARCH>`
