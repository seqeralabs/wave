---
title: Wave containers
date: 2023-03-11
tags: [overview, deployment, hosted, self-hosted, enterprise]
---

Wave is a container provisioning service. It builds, augments, and serves container images on demand. Pipelines declare their dependencies and Wave returns an image URI tailored to each task. The process is automated and transparent to the container runtime.

Wave removes the need to pre-build and maintain a catalog of images for every pipeline task. Build instructions live in the pipeline. Wave assembles the image when it is needed.

## Wave deployments

The hosted Wave containers service is free and supports every feature. Anyone using Nextflow, the Wave CLI, or the API can use it.

Seqera Enterprise customers can also license Wave for self-hosted deployment. Self-hosted Wave is available in two configurations:

- **Wave**: the full feature set, including augmentation, inspection, authentication, on-demand builds, freeze, mirroring, and security scanning.
- **Wave Lite**: a reduced configuration with container augmentation, inspection, and private registry authentication. Suits regulated or air-gapped environments where on-cluster builds are not required.

:::info
[Contact us](https://seqera.io/contact-us/) for more information about self-hosted deployments.
:::

## Seqera Containers

[Seqera Containers](./seqera-containers/index.mdx) is a free public registry operated by Seqera and powered by Wave. It produces Conda and PyPI images on demand and publishes them to `community.wave.seqera.io`. Images are public, free of charge, and require no account.

See [Seqera Containers](./seqera-containers/index.mdx) for more information.
