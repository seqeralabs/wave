---
title: Reduce Wave API calls
description: Learn how to use Wave freeze to reduce API calls and avoid rate limits in large-scale Nextflow pipelines
date created: 2025-09-30
date edited: 2025-09-30
tags: [nextflow, wave, rate limits, use cases, guides]
---

Wave rate limits can affect large-scale pipelines that pull containers across thousands of concurrent tasks. This guide shows you how to use Wave freeze to reduce API calls and avoid rate limits.

## How Wave rate limits work

Wave applies rate limits to manifest requests, not layer requests. With an access token, you can pull 2,000 containers per minute. When you pull a container:

- The manifest request counts as one pull against your rate limit.
- Layer and blob requests don't count against rate limits.
- A container with 100 layers counts as 1 pull.

Rate limits affect pipelines with high concurrency. The following example demonstrates this issue:

- 50 concurrent pipeline runs
- Each run spawns 10,000 tasks
- Each task pulls a container
- Total: 500,000 manifest requests

This volume exceeds the 2,000 per minute limit and causes failed tasks and pipeline errors.

## Use Wave freeze to avoid rate limits

Wave freeze builds your container once and stores it in your registry. After the initial build, all container pulls bypass Wave.

**Augmentation without freeze:**

1. Each task requests a manifest from Wave.
1. Wave retrieves the base image from the source registry.
1. Wave augments the image with the fusion layer.
1. Wave returns the modified manifest.
1. Every task creates one API call to Wave.

With thousands of concurrent tasks, this approach exceeds rate limits.

**Augmentation with freeze:**

1. Wave builds the image once with your specifications.
1. Wave pushes the complete image to your registry.
1. Wave stores the manifest in its database.
1. Wave returns a direct URL to your registry.
1. All future pulls go directly to your registry.

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

- `<BUILD_REPOSITORY>`: the repository to store your built containers
- `<TOWER_ACCESS_TOKEN>`: your Seqera access token

### Container registry selection

**Recommended**: Use Amazon ECR for AWS Batch workloads

Amazon ECR provides the following benefits:

- Automatic authentication through IAM roles.
- No manual credential configuration
- Lowest latency for AWS workloads.
- Simplest setup.

**Not recommended**: Private Docker Hub for AWS Batch workloads

Private Docker Hub has the following limitations:

- Requires manual credential configuration on each compute instance.
- Additional security overhead.
- More complex authentication setup.

If you use private Docker Hub, configure Docker credentials on each compute instance.

### First pipeline run

When you run your pipeline for the first time with Wave freeze:

1. The Nextflow head job sends your build request to Wave.
1. Wave checks whether the requested images already exist.
1. Wave builds any missing images and pushes them to your registry.
1. Wave returns the final registry URLs.
1. Your compute tasks pull images directly from your registry.

The initial build counts against build limits, not pull limits. Build limits are significantly higher than pull limits.

### Subsequent pipeline runs

When you run your pipeline again:

1. The Nextflow head job contacts Wave to check for existing images.
1. Wave finds the cached images (matched by content hash).
1. Wave returns the registry URLs immediately without rebuilding.
1. All container pulls go directly to your registry.
1. No Wave API calls occur during task execution.

Rate limit issues are eliminated because manifest requests happen at the registry level, not through Wave.

## Remove Wave from production pipelines

For containers that change infrequently, you can remove Wave from your production workflows.

### Extract container URLs

To run your pipeline without Wave:

1. Run your pipeline with Wave freeze enabled:

    ```bash
    nextflow run main.nf
    ```

1. Inspect the pipeline to view the container URLs that Wave created:

    ```bash
    nextflow inspect main.nf
    ```

1. Update your pipeline and configuration:
    1. Copy the registry URLs from the inspect output.
    1. Update your pipeline to use these direct URLs.
    1. Set `wave.enabled = false` in your configuration.
    1. Remove Wave dependencies from your setup.

After you complete these steps, all container pulls go directly to your registry.

### When to use this approach

Remove Wave from production pipelines when:

- Your containers change infrequently (monthly or yearly).
- You need maximum performance and minimal dependencies.
- You want complete control over container versions.
- You can manage a periodic rebuild process.

Don't remove Wave when:

- You frequently update container dependencies.
- You're actively developing and testing workflows.
- You need Wave's dynamic container building features.
