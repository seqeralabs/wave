---
title: Overview
---

With Wave and Nextflow, you can:

- Build container images automatically from Dockerfiles or Conda environments
- Access private container repositories securely
- Mirror containers across registries for improved performance
- Provision Singularity containers
- Freeze container versions for reproducible workflows
- Integrate with Fusion file system

:::note
Nextflow integration with Wave requires Nextflow 22.10.0 or later.
:::

## Get started

To enable Wave in your Nextflow pipeline, add the following to your Nextflow configuration file:

```groovy
wave.enabled = true
tower.accessToken = '<TOWER_ACCESS_TOKEN>'
```

Replace `<TOWER_ACCESS_TOKEN>` with your Seqera access token.

:::note

Using a Seqera access token is optional but provides additional capabilities:

- Access to private repositories
- Higher API request limits than anonymous users

:::

## Use Cases

Wave supports several key use cases that enhance your Nextflow pipeline capabilities:

### Container building and provisioning

Wave can automatically build container images from Dockerfiles placed in your module directories or provision containers based on conda package specifications. This eliminates the need to pre-build and manage container images manually.

### Private repository access

Securely access private container repositories using credentials managed through the Seqera Platform. Wave handles authentication automatically, allowing your pipelines to pull from private registries without exposing sensitive credentials.

### Container mirroring

Mirror containers from public registries to your own private registry for improved performance, compliance, and availability. This ensures your pipelines can access required containers even when upstream registries are unavailable.

### Conda package integration

Use conda packages directly in cloud-native environments that don't natively support conda. Wave builds containers with your specified conda dependencies, enabling portable execution across different compute platforms.

### Singularity container support

Build and provision Singularity native images for HPC environments while maintaining compatibility with container registries through ORAS artifacts.

### Fusion file system integration

Combine Wave containers with Fusion to use cloud object storage as your pipeline work directory, simplifying data management and improving performance on cloud platforms.

## Configuration

Wave provides extensive configuration options to customize container provisioning behavior, scanning behavior, caching strategies, and repository settings.

For complete configuration reference and advanced options, see [Reference](./reference.md).
