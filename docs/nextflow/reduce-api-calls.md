---
title: Reducing Wave API calls
description: Learn how to freeze containers and reduce API calls in large-scale Nextflow pipelines
date created: 2025-09-30
date edited: 2025-10-31
tags: [nextflow, wave, rate limits, guides]
---

Large-scale pipelines that pull container images across thousands of concurrent tasks can encounter Wave rate limits. This guide describes how to use Wave freeze to reduce API calls and avoid rate limits.

:::note
Wave applies rate limits to container builds and pulls (manifest requests). Authenticated users have higher rate limits than anonymous users. See [API limits](../api/index.md#api-limits) for more information.
:::

## How Wave pull rate limits work

When you pull a container image:

1. The Docker service downloads the container manifest (a JSON file detailing each layer of the final image).
1. The Docker service downloads each layer listed in the manifest file.

Wave defines a pull as downloading both the container manifest and all layers. Therefore:

- The manifest request to Wave counts as one pull against your rate limit
- Layer and blob requests don't count against rate limits
- A container image with 100 layers counts as 1 pull

Rate limits affect pipelines with high concurrency. The following example demonstrates this issue:

- 50 concurrent pipeline runs
- Each run spawns 10,000 tasks
- Each task runs on it's own VM
- Each task pulls a container image
- 500,000 manifest requests are made

This volume exceeds the 2,000 container pulls per minute limit and causes failed tasks and pipeline errors.

## How Wave freeze avoids rate limits

Wave freeze builds your container image once and stores it in your registry. After the initial build, the source of the container image manifest and layers are redirected to your private registry by Wave.

### Building without Wave freeze

When you run your pipeline without Wave freeze:

1. Each task requests a manifest from Wave.
1. Wave performs one of the following actions:
    - Retrieves the base image manifest from the source registry
    - Builds the image from a Dockerfile
    - Builds the image from a Conda definition
1. Wave injects the Fusion layer to the container image manifest.
1. Wave stores the final manifest on Seqera infrastructure.
1. Wave returns the modified manifest.

This approach exceeds rate limits with thousands of concurrent tasks.

### Building with Wave freeze

When you run your pipeline with Wave freeze for the first time:

1. The Nextflow head job sends your build request to Wave.
1. Wave checks whether the requested images already exist.
1. Wave builds any missing images and pushes the manifest and layers to your registry.
1. Wave returns the final registry URLs.
1. Your compute tasks pull images directly from your registry.

When you run your pipeline with Wave freeze again:

1. The Nextflow head job contacts Wave to request the frozen images.
1. Wave finds the frozen images in your registry (matched by content hash).
1. Wave returns the container URLs in the destination container registry without rebuilding.
1. All tasks pull the image directly from your registry.

With freeze enabled, only the first API call to Wave counts toward your quota.
Wave reuses frozen images as long as the image and its configuration remain the same.
This prevents rate limit issues because manifest requests happen at the registry level, not through Wave.

:::note
For pipelines with stable containers, you can prevent Wave API calls by pre-resolving URLs with [`nextflow inspect`](https://nextflow.io/docs/latest/reference/cli.html#inspect) or [Wave CLI](../cli/index.md), then using the resolved registry URLs directly in your configuration. Keep Wave enabled during active development or when using dynamic container features to build container images at runtime.
:::

## Configure Wave freeze

To configure Wave freeze, add the following configuration to your Nextflow pipeline:

```groovy
fusion.enabled = true // Recommended (optimizes frozen images for cloud storage)
tower.accessToken = '<TOWER_ACCESS_TOKEN>' // Required
wave.enabled = true // Required
wave.freeze = true // Required
wave.build.repository = '<BUILD_REPOSITORY>' // Required
wave.build.cacheRepository = '<CACHE_REPOSITORY>' // Recommended (accelerates builds by reusing unchanged layers)
```

Replace the following:

- `<TOWER_ACCESS_TOKEN>`: your [Platform access token](../tutorials/nextflow-wave.mdx#create-your-seqera-access-token)
- `<BUILD_REPOSITORY>`: the container registry URL where Wave uploads built images
- `<CACHE_REPOSITORY>`: the container registry URL for caching image layers built by the Wave service

## Container image tags

Use specific version tags (such as `ubuntu:22.04`) or SHA256 digests with Wave freeze.
Specific tags enable Wave to match content hashes and reuse frozen images.
This ensures reproducibility and eliminates unnecessary rebuilds.
Avoid using the `latest` tag because it points to different image versions over time.

## Container registry selection

**Recommended**: Use your cloud provider's native container registry for the simplest setup and integration.

Native cloud registries have the following benefits:

- Automatic authentication through cloud IAM roles
- Low latency for workloads in the same cloud region
- Simple setup and configuration
- Native integration with your cloud platform

Examples of native registries by cloud provider:

- **AWS**: Amazon Elastic Container Registry (ECR)
- **Azure**: Azure Container Registry (ACR)
- **Google Cloud**: Google Artifact Registry

**Alternative option**: Third-party container registries.

Third-party registries (e.g., Docker Hub or Quay.io) require additional setup and have the following requirements:

- Manual credential configuration on each compute instance
- Public endpoints for Wave to connect to
- Additional security configuration
- More complex authentication setup compared to native registries
