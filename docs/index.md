---
title: Wave containers
---

Containers are an essential part of modern data analysis pipelines in bioinformatics. They encapsulate applications and dependencies in portable, self-contained packages that can be easily distributed across diverse computing environments. Containers are also key to enabling predictable and reproducible scientific results.

However, workflows can comprise dozens of distinct container images. Pipeline developers must manage and maintain these container images and ensure that their functionality precisely aligns with the requirements of every pipeline task, creating unnecessary friction in the maintenance and deployment of data pipelines.

Wave solves this problem by provisioning containers on-demand during pipeline execution. This allows the delivery of container images that are defined precisely depending on the requirements of each pipeline task in terms of dependencies and platform architecture. This process is completely transparent and fully automated, removing all the plumbing and friction commonly needed to create, upload, and maintain multiple container images required for pipeline execution.

To get started with Wave:

1. See the Wave [Tutorials][wave-tutorials]
1. Learn about [Nextflow integration][nf]
1. Learn about the [Wave CLI][cli]

:::note
Wave is also available as a hosted service on [Seqera Platform](https://cloud.seqera.io/). For Seqera Enterprise customers, a licensed self-hosted Wave solution is also available. [Contact us](https://seqera.io/contact-us/) for more information.
:::

[wave-tutorials]: ./tutorials/index.mdx
[nf]: ./nextflow/index.md
[cli]: ./cli/index.md

## Wave features

### Private registry authentication

Wave integrates with Seqera Platform credentials management, which enables seamless access to private container registries and publishing to them for freeze and mirror operations.

See [Private registry authentication](./features/authentication.mdx) for more information.

### Seqera Containers

[Seqera Containers](https://seqera.io/containers/) is a free community service operated by Seqera. It uses Wave to build images from Conda and PyPI packages on demand through the [web interface](https://seqera.io/containers/), the [Wave CLI](./cli/index.md), or the [Nextflow integration](./nextflow/index.md).

Images are cached and hosted permanently in a [Docker Distribution][docker] registry on AWS infrastructure, served through a Cloudflare CDN. Images are publicly accessible to anyone free of charge and are stored for at least five years. They can be pulled from any infrastructure (local, HPC, or cloud) as Docker or native Singularity images, and can be built for `linux/amd64` and `linux/arm64` architectures.

:::note
Seqera Containers does not work with custom container files, augmentation, or authorization. It provides only Conda- and PyPI-based containers.
:::

See [Seqera Containers](./seqera-containers/index.mdx) for more information.

[docker]: https://github.com/distribution/distribution

### Container augmentation

Wave offers a flexible approach to container image management. It allows you to dynamically add custom layers to existing container images, creating new images tailored to your specific needs. Any existing container can be extended without rebuilding it. User-provided content, such as custom scripts, configuration files, and logging agents, can be integrated into pre-existing container images.

See [Container augmentation](./features/augmentation.mdx) for more information.

### Conda-based container builds

Package management systems such as Conda and Bioconda simplify the installation of scientific software. Wave enables dynamic provisioning of container images from any Conda or Bioconda recipe. Declare the Conda packages in your Nextflow pipeline and Wave assembles the required container.

See [On-demand container builds](./features/container-builds.mdx) for more information.

### Singularity container builds

Singularity and Apptainer use a proprietary format called Singularity Image Format (SIF). Wave can provision containers in SIF format from a Singularityfile or a Conda environment specification. The resulting Singularity image is stored as an ORAS artifact in an OCI-compliant container registry of your choice or in the Wave community registry.

Singularity and Apptainer engines can pull and execute these images natively without the image-conversion step required for Docker images.

:::note
Wave's freeze mode is required when provisioning Singularity images because the monolithic SIF format does not support dynamic layer injection.
:::

See [On-demand container builds](./features/container-builds.mdx) for more information.

### Deploying containers across multi-clouds

Cloud vendors provide integrated container registries with better performance and cost-efficiency than central, remote registries. Storing container images in a private registry also enhances security and provides faster access with greater control. Wave mirroring addresses these needs by copying containers to your chosen registry while preserving the original manifest, image name, and hash, and ensuring that images remain unmodified and accessible via the original build hash.

See [Container mirroring](./features/mirroring.mdx) for more information.

### Container security scanning

Builds for OCI-compliant container images are scanned for known security vulnerabilities. Wave conducts a vulnerability scan using the [Trivy](https://trivy.dev/) security scanner. Seqera Platform customers receive an email with a link to the security report listing any vulnerabilities discovered.

See [Security scanning](./features/security.mdx) for more information.

### Wave Lite

Wave can be used in Lite mode for container augmentation, container inspection, and private registry authentication. Wave Lite can be deployed with Docker Compose or Kubernetes.

See [Features](./features/index.mdx) for more information.
