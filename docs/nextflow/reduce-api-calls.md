---
title: Reduce Wave API calls
description: Learn how to freeze containers and reduce API calls in large-scale Nextflow pipelines
date created: 2025-09-30
date edited: 2025-10-01
tags: [nextflow, wave, rate limits, guides]
---

Wave rate limits can affect large-scale pipelines that pull container images across thousands of concurrent tasks. This guide shows you how to use Wave freeze to reduce API calls and avoid rate limits.

## API limits

Wave applies rate limits to container builds and pulls (manifest requests). Authenticated users have higher rate limits than anonymous users.

If an access token is provided, the following rate limits apply:

- 250 container builds per hour
- 2,000 container pulls (manifest requests) per minute

If an access token isn't provided, the following rate limits apply:

- 25 container builds per day
- 100 container pulls (manifest requests) per hour

## How Wave pull rate limits work

When you pull a container image:

- The manifest request counts as one pull against your rate limit
- Layer and blob requests don't count against rate limits
- A container image with 100 layers counts as 1 pull

Rate limits affect pipelines with high concurrency. The following example demonstrates this issue:

- 50 concurrent pipeline runs
- Each run spawns 10,000 tasks
- Each task pulls a container image
- Total: 500,000 manifest requests

This volume exceeds the 2,000 per minute limit and causes failed tasks and pipeline errors.

## How Wave freeze to avoids rate limits

Wave freeze builds your container image once and stores it in your registry. After the initial build, the source for the container image manifest and layers are redirected to your private registry by Wave.

### Building without Wave freeze

When you run your pipeline without Wave freeze:

1. Each task requests a manifest from Wave.
1. Wave either retrieves the base image manifest from the source registry, builds the image from a Dockerfile, or builds the image from a Conda definition.
1. Wave injects the Fusion layer to the container image manifest.
1. Wave stores the built container images on Seqera infrastructure.
1. Wave returns the modified manifest.
1. Every task creates one API call to Wave.

With thousands of concurrent tasks, this approach exceeds rate limits.

### Building with Wave freeze

When you run your pipeline with Wave freeze for the first time:

1. The Nextflow head job sends your build request to Wave.
1. Wave checks whether the requested images already exist.
1. Wave builds any missing images and pushes them to your registry.
1. Wave returns the final registry URLs.
1. Your compute tasks pull images directly from your registry.

When you run your pipeline with Wave freeze again:

1. The Nextflow head job contacts Wave to request the frozen images.
1. Wave's rate limiting applies to this request.
1. Wave finds the frozen images in your registry (matched by content hash).
1. Wave returns the registry URLs immediately without rebuilding.
1. All tasks pull the image directly from your registry.
1. The same frozen image serves many task executions.

With freeze enabled, only the first API call to Wave counts towards your quota.
Frozen images will be reused as long as the image and its configuration remain the same.
Rate limit issues are eliminated because manifest requests happen at the registry level, not through Wave.

:::note
For stable container images, you can run `nextflow inspect` to generate a Nextflow configuration that includes resolved container registry URLs. These can then be used within Nextflow as an additional configuration file. Keep Wave enabled during active development or when using dynamic container features to build container images at runtime.
:::

## Configure Wave freeze

To configure Wave freeze, add the following configuration to your Nextflow pipeline:

```groovy
fusion.enabled = true // Recommended
tower.accessToken = '<TOWER_ACCESS_TOKEN>' // Required
wave.enabled = true // Required
wave.freeze = true // Required
wave.build.repository = '<BUILD_REPOSITORY>' // Required
wave.build.cacheRepository = '<CACHE_REPOSITORY>' // Recommended
```

Replace the following:

- `<TOWER_ACCESS_TOKEN>`: your Seqera access token
- `<BUILD_REPOSITORY>`: the container registry URL where Wave uploads built images
- `<CACHE_REPOSITORY>`: the container registry URL for caching image layers built by the Wave service

:::note
Specify `wave.build.cacheRepository` to accelerate container builds.
The cache reuses unchanged layers to reduce build times and costs.
:::

:::note
Fusion isn't required for Wave freeze.
However, Fusion ensures that the frozen images contain the necessary components for optimal performance when when accessing cloud object storage.
:::

## Container registry selection

**Recommended**: Use Amazon ECR for AWS Batch workloads

Amazon ECR has the following benefits:

- Automatic authentication through IAM roles
- No manual credential configuration
- Lowest latency for AWS workloads
- Simplest setup

**Not recommended**: External container registries for AWS Batch workloads

External container registries have following limitations:

- Requires manual credential configuration on each compute instance
- Additional security overhead
- More complex authentication setup

If you use external container registries, configure your credentials on each compute instance.
