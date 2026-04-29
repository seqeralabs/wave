---
title: Wave containers
---

Wave is a container provisioning service. It builds, augments, and serves container images on demand. Pipelines declare their dependencies and Wave returns an image URI tailored to each task. The process is automated and transparent to the container runtime.

Wave removes the need to pre-build and maintain a catalog of images for every pipeline task. Build instructions live in the pipeline. Wave assembles the image when it is needed.

## Wave and Wave Lite

The Seqera Wave service supports the full feature set and is available to anyone using Nextflow, the Wave CLI, or the API.

Seqera Enterprise customers can also license a self-hosted deployment in two configurations:

- **Wave Lite**: container augmentation, inspection, and private registry authentication.
- **Wave**: Wave Lite plus on-demand builds, freeze, mirroring, and security scanning.

[Contact Seqera](https://seqera.io/contact-us/) for more information.

## Seqera Containers

[Seqera Containers](./seqera-containers/index.mdx) is a free public registry operated by Seqera and powered by Wave. It produces Conda and PyPI images on demand and publishes them to `community.wave.seqera.io`. Images are public, free of charge, and require no account.

See [Seqera Containers](./seqera-containers/index.mdx) for more information.

## Next steps

To start using Wave:

- Read [How it works](./how-it-works.mdx) for the request lifecycle and image URI formats.
- Browse [Features](./features/index.mdx) for the full capability set.
- Follow the [Tutorials](./tutorials/index.mdx) to provision your first container.
