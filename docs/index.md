---
title: Wave
date: 2023-03-11
tags: [overview, deployment, hosted, self-hosted, enterprise]
---

Wave is a container provisioning service. It builds, augments, and serves container images on demand. Pipelines declare their dependencies. Wave returns an image URI tailored to each task. The process is automated and transparent to the container runtime.

Wave removes the need to pre-build and maintain a catalog of images for every pipeline task. Build instructions live in the pipeline. Wave assembles the image when it is needed.

## Wave deployments

Seqera runs Wave as a free hosted service. It supports every feature and is open to anyone using Nextflow, the Wave CLI, or the Wave API.

Seqera Enterprise customers can also license Wave for self-hosted deployment. Self-hosted Wave is available in two configurations:

- **Wave**: the full feature set: container augmentation, inspection, private registry authentication, on-demand builds, freeze, mirroring, and security scanning.
- **Wave Lite**: a reduced configuration with container augmentation, inspection, and private registry authentication. It suits regulated or air-gapped environments that do not need on-cluster builds.

:::info
[Contact us](https://seqera.io/contact-us/) for more information about self-hosted deployments.
:::

## Seqera Containers

[Seqera Containers](./seqera-containers.md) is a free public registry operated by Seqera and powered by Wave. It builds Conda and PyPI images on demand and publishes them to `community.wave.seqera.io`. Images are public and require no account.

See [Seqera Containers](./seqera-containers.md) for more information.
