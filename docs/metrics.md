---
title: Usage metrics
---

Wave uses Redis to store its usage metrics for a specific date and/or a specific organization.

These are stored using the following keys:

- `pulls/d/YYYY-MM-DD`
- `pulls/o/<org>`
- `pulls/o/<org>/d/YYYY-MM-DD`
- `fusion/d/YYYY-MM-DD`
- `fusion/o/<org>`
- `fusion/o/<org>/d/YYYY-MM-DD`
- `builds/d/YYYY-MM-DD`
- `builds/o/<org>`
- `builds/o/<org>/d/YYYY-MM-DD`
- `pulls/a/<arch>/d/YYYY-MM-DD`
- `pulls/o/<org>/a/<arch>`
- `pulls/o/<org>/a/<arch>/d/YYYY-MM-DD`
- `pulls/a/<arch>`
- `fusion/a/<arch>/d/YYYY-MM-DD`
- `fusion/o/<org>/a/<arch>`
- `fusion/o/<org>/a/<arch>/d/YYYY-MM-DD`
- `fusion/a/<arch>`
- `builds/a/<arch>/d/YYYY-MM-DD`
- `builds/o/<org>/a/<arch>`
- `builds/o/<org>/a/<arch>/d/YYYY-MM-DD`
- `builds/a/<arch>`
- `mirrors/a/<arch>/d/YYYY-MM-DD`
- `mirrors/o/<org>/a/<arch>`
- `mirrors/o/<org>/a/<arch>/d/YYYY-MM-DD`
- `mirrors/a/<arch>`

## Functionality

### Store Builds

When Wave launches a build, it also increments the values of following keys in Redis:

- `builds/d/YYYY-MM-DD`
- `builds/o/<org>`
- `builds/o/<org>/d/YYYY-MM-DD`

### Store Pulls

Wave tracks the container image pulls using io.seqera.wave.filter.PullMetricsRequestsFilter, where it checks if `Content-Type` header contains one of the following values:

- `application/vnd.docker.distribution.manifest.v2+json`
- `application/vnd.oci.image.manifest.v1+json`
- `application/vnd.docker.distribution.manifest.v1+prettyjws`
- `application/vnd.docker.distribution.manifest.v1+json`

Then it increments the values of following keys in Redis:

- `pulls/d/YYYY-MM-DD`
- `pulls/o/<org>`
- `pulls/o/<org>/d/YYYY-MM-DD`

Then, if the pulled container uses fusion, it increments the values of following keys in Redis:

- `fusion/d/YYYY-MM-DD`
- `fusion/o/<org>`
- `fusion/o/<org>/d/YYYY-MM-DD`

## How keys are created

- When a request is made to wave, first it increments the key with current date. e.g. `builds/d/2024-04-23`.
- Keys with organization are only incremented if the user is authenticated means there is Seqera platform token in the request.
- Wave extract the domain from the user email id (For example: `test_metrics@seqera.io`), which it gets from Seqera platform using the access token.
- In this case, The organization value will be `seqera.io`.
- Then it increments the key with organization. For example: `builds/o/seqera.io/d/2024-04-23` and `builds/o/seqera.io`.
