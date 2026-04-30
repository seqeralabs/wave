---
title: Seqera Containers
description: A free community registry powered by Wave for building Conda and PyPI container images on demand.
date: 2026-04-22
tags: [seqera-containers, conda, pypi, public-registry, community]
---

[Seqera Containers](https://seqera.io/containers/) is a free public registry operated by Seqera and powered by Wave. It uses Wave's [on-demand build](../features/container-builds.mdx) and [freeze](../features/container-freezes.mdx) capabilities to produce Conda and PyPI container images, then publishes them to `community.wave.seqera.io`. Anyone can request an image through the [web interface](https://seqera.io/containers/), the [Wave CLI](https://docs.seqera.io/wave/cli), or the [Nextflow integration](https://docs.seqera.io/wave/nextflow). No account is required.

Images are hosted in the Wave community container registry at [`community.wave.seqera.io`](https://community.wave.seqera.io/). Seqera stores them for at least five years. Images are public and free of charge. They can be pulled from local, HPC, or cloud infrastructure as Docker or native Singularity images.

Supported architectures include `linux/amd64` and `linux/arm64`.

:::note
Seqera Containers only builds Conda- and PyPI-based images. It does not support custom container files, augmentation, or private registry authentication.

You can still use a Seqera Containers image as a base for Wave augmentation, mirroring, freeze mode, and other features.
:::

## Use cases

Use cases for Seqera Containers include:

- **Rapid prototyping**: Create a container for any Conda or PyPI package without a local build.
- **Reproducibility**: Share a stable image URI with collaborators. The same hash returns the same image for at least five years.
- **Community tooling**: Use pre-built images in tutorials, workshops, and demos.

## Web interface

Request an image from Seqera Containers without a software installation or registration:

1. Visit [https://seqera.io/containers](https://seqera.io/containers).

2. Search for Conda packages or packages on the Python Package Index.

    :::note
    By default, Conda package names are searched in the `bioconda` and `conda-forge` channels. Prepend a channel name with `::` to search a specific channel. For example, `nvidia::cuda`.
    :::

3. Open **Container settings** and select Docker or Singularity, plus `linux/amd64` or `linux/arm64`.

4. Select **Get Container** to see the resulting container URI.

    :::note
    A spinner shows build progress. If you selected Singularity, a toggle appears below the URI.
    :::

5. Select **View build details** to see information about the container, the build, and the security scan.
