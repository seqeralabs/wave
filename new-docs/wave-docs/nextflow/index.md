---
title: Overview
description: Learn about using Wave with Nextflow
tags: [nextflow, wave, get started]
---

Wave's container provisioning service seamlessly integrates with Nextflow pipelines to streamline container management and deployment. When used with Nextflow, Wave automatically handles container building, provisioning, and optimization, eliminating the complexity of manual container management in your workflow execution.

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

## Use cases

Wave supports several key use cases that enhance your Nextflow pipeline capabilities:

### Private repository access

Securely access private container repositories using credentials managed through the Seqera Platform. Wave handles authentication automatically, allowing your pipelines to pull from private registries without exposing sensitive credentials. See [Access private container repositories](./use-cases.md#access-private-container-repositories) for more information.

### Container building and provisioning

Wave can automatically build container images from Dockerfiles placed in your module directories or provision containers based on Conda package specifications. This eliminates the need to pre-build and manage container images manually. See [Build Nextflow module containers](./use-cases.md#build-nextflow-module-containers) for more information.

### Conda package integration

Use Conda packages directly in cloud-native environments that don't natively support Conda. Wave builds containers with your specified Conda dependencies, enabling portable execution across different compute platforms. See [Build Conda-based containers](./use-cases.md#build-conda-based-containers) for more information.

### Singularity container support

Build and provision Singularity native images for HPC environments while maintaining compatibility with container registries through ORAS artifacts. See [Build Singularity containers](./use-cases.md#build-singularity-containers) for more information.

### Container mirroring

Mirror containers from public registries to your own private registry for improved performance, compliance, and availability. This ensures your pipelines can access required containers even when upstream registries are unavailable. See [Mirror containers across registries](./use-cases.md#mirror-containers-across-registries) for more information.

### Security scanning

Scan your Nextflow pipeline containers automatically with Wave to identify security vulnerabilities before they become a problem. This proactive security check gives you confidence that your workflows run on secure images. It helps you address potential risks early and maintain robust, trustworthy pipelines. See [Security scan containers](./use-cases.md#security-scan-containers) for more information.

### Fusion file system integration

Combine Wave containers with Fusion to use cloud object storage as your pipeline work directory, simplifying data management and improving performance on cloud platforms. See [Use Wave with Fusion](./use-cases.md#use-wave-with-fusion) for more information.

## Configuration

Wave provides extensive configuration options to customize container provisioning behavior, scanning behavior, caching strategies, and repository settings.

For detailed configuration options and advanced settings, see [Configuration reference](./configuration.md).
