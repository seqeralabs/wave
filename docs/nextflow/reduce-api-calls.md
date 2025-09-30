---
title: Reduce Wave API calls
description: Learn how to use Wave freeze to reduce API calls and avoid rate limits in large-scale Nextflow pipelines
date created: 2025-09-30
date edited: 2025-09-30
tags: [nextflow, wave, rate limits, guides]
---

Wave rate limits can affect large-scale pipelines that pull containers across thousands of concurrent tasks. This guide shows you how to use Wave freeze to reduce API calls and avoid rate limits.

## API limits

Wave applies rate limits to container builds and pulls. Authenticated users have higher rate limits than anonymous users.

If an access token is provided, the following rate limits apply:

- 250 container builds per hour
- 2,000 container pulls per minute

If an access token isn't provided, the following rate limits apply:

- 25 container builds per day
- 100 container pulls per hour

## How Wave pull rate limits work

When you pull a container:

- The manifest request counts as one pull against your rate limit
- Layer and blob requests don't count against rate limits
- A container with 100 layers counts as 1 pull

Rate limits affect pipelines with high concurrency. The following example demonstrates this issue:

- 50 concurrent pipeline runs
- Each run spawns 10,000 tasks
- Each task pulls a container
- Total: 500,000 manifest requests

This volume exceeds the 2,000 per minute limit and causes failed tasks and pipeline errors.

## Use Wave freeze to avoid rate limits

Wave freeze builds your container once and stores it in your registry. After the initial build, the source for the container manifest and layers are redirected to your private registry by Wave.

**Building without freeze:**

1. Each task requests a manifest from Wave
1. Wave either retrieves the base image manifest from the source registry, builds the image from a Dockerfile or builds the image from a Conda definition.
1. Wave builds the image with the fusion layer
1. Wave returns the modified manifest
1. Every task creates one API call to Wave

With thousands of concurrent tasks, this approach exceeds rate limits.

**Building with freeze:**

1. Wave retrieves the manifest or builds the image once with your specifications
1. Wave pushes the complete image manifest and layers to your registry
1. Wave returns a direct URL to your registry
1. All future pulls go directly to your registry

With freeze enabled, Wave is removed from the container pull path. Your compute instances pull directly from your registry with no Wave API calls.

## Configure Wave freeze

To configure Wave freeze, add the following configuration to your Nextflow pipeline:

```groovy
wave.enabled = true
wave.freeze = true
wave.build.repository = '<BUILD_REPOSITORY>'

tower.accessToken = '<TOWER_ACCESS_TOKEN>'
```

Replace the following:

- `<BUILD_REPOSITORY>`: your repository to store and access built containers
- `<TOWER_ACCESS_TOKEN>`: your Seqera access token

### Container registry selection

**Recommended**: Use Amazon ECR for AWS Batch workloads

Amazon ECR has the following benefits:

- Automatic authentication through IAM roles
- No manual credential configuration
- Lowest latency for AWS workloads
- Simplest setup

**Not recommended**: Private Docker Hub for AWS Batch workloads

Private Docker Hub has the following limitations:

- Requires manual credential configuration on each compute instance
- Additional security overhead
- More complex authentication setup

If you use private Docker Hub, configure Docker credentials on each compute instance.

### First pipeline run

When you run your pipeline for the first time with Wave freeze:

1. The Nextflow head job sends your build request to Wave
1. Wave checks whether the requested images already exist
1. Wave builds any missing images and pushes them to your registry
1. Wave returns the final registry URLs
1. Your compute tasks pull images directly from your registry

The initial build counts against build limits, not pull limits

### Subsequent pipeline runs

When you run your pipeline again:

1. The Nextflow head job contacts Wave to check for existing images
1. Wave finds the cached images (matched by content hash)
1. Wave returns the registry URLs immediately without rebuilding
1. All container pulls go directly to your registry
1. Wave API calls do not count against quota.

Rate limit issues are eliminated because manifest requests happen at the registry level, not through Wave.

:::note
For stable containers, you can run `nextflow inspect` to generate a Nextflow configuration that includes resolved container URLs from your repository, which can then be used within Nextflow as an additional configuration file. Keep Wave enabled during active development or when using dynamic container features to build containers at runtime.
:::

