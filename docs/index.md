---
title: Wave containers
---

Containers are an essential part of modern data analysis pipelines in bioinformatics. They encapsulate applications and dependencies in portable, self-contained packages that can be easily distributed across diverse computing environments. Containers are also key to enabling predictable and reproducible scientific results.

However, workflows can comprise dozens of distinct container images. Pipeline developers must manage and maintain these container images and ensure that their functionality precisely aligns with the requirements of every pipeline task, creating unnecessary friction in the maintenance and deployment of data pipelines.

Wave solves this problem by provisioning containers on-demand during pipeline execution. This allows the delivery of container images that are defined precisely depending on the requirements of each pipeline task in terms of dependencies and platform architecture. This process is completely transparent and fully automated, removing all the plumbing and friction commonly needed to create, upload, and maintain multiple container images required for pipeline execution.

To get started with Wave:

1. See the [Get started][started] guides.
1. Learn about [Nextflow integration][nf].
1. Learn about the [Wave CLI][cli].

:::note
Wave is also available as a hosted service on [Seqera Platform](https://cloud.seqera.io/). For Seqera Enterprise customers, a licensed self-hosted Wave solution is also available. [Contact us](https://seqera.io/contact-us/) for more information.
:::

[started]: ./tutorials/index.md
[nf]: ./nextflow/index.md
[cli]: ./cli/index.md

## Wave features

### Container registries

#### Private container registries

Wave integrates with [Seqera Platform credentials management][private] enabling seamless access and publishing to private registries.

[private]: ./nextflow/use-cases.md#access-private-container-repositories

#### Seqera Containers - The community container registry

[Seqera Containers] is a free community service operated by Seqera.

It uses Wave to build images from Conda / PyPI packages on demand, either through the [web interface](https://seqera.io/containers/) or using the [Wave CLI](./cli/index.md) / [Nextflow integration](./nextflow/index.md).

These images are cached and hosted permanently, being served through a [Docker Distribution][docker] registry and hosted on AWS infrastructure. Images are cached and served via Cloudflare CDN.

Images are publicly accessible to anyone for free and will be stored for at least 5 years. They can be pulled using any infrastructure (local, HPC, cloud) as Docker or native Singularity images. Images can be built for both `linux/aarch64` and `linux/arm64` architectures.

:::note
Seqera Containers does not work with custom container files, augmentation, or authorization. It provides only Conda based containers.
:::

[docker]: https://github.com/distribution/distribution
[Seqera Containers]: https://seqera.io/containers/

### Augment existing containers

Wave offers a flexible approach to container image management. It allows you to [dynamically add custom layers][augment] to existing docker images, creating new images tailored to your specific needs.
Any existing container can be extended without rebuilding it. You can add user-provided content such as custom scripts and logging agents, providing greater flexibility in the container’s configuration.

[augment]: ./provisioning.md#container-augmentation

### Conda-based containers

Package management systems such as Conda and Bioconda simplify the installation of scientific software.
Wave enables dynamic provisioning of container images from any Conda or Bioconda recipe. Just [declare the Conda packages][conda] in your Nextflow pipeline and Wave will assemble the required container.

[conda]: ./nextflow/use-cases.md#build-conda-based-containers

### Singularity containers

Singularity and Apptainer use a proprietary format called _Singularity Image Format_ (SIF). The Wave service can [provision containers based on the Singularity image format][singularity] either by using a `Singularityfile` file or Conda packages. The resulting Singularity image file is stored as an ORAS artifact in an OCI-compliant container registry of your choice or the Wave Community registry.

The advantage of this approach is that Singularity and Apptainer engines can pull and execute container images natively without the extra conversion steps needed when using Docker images with these engines.

:::note
Due to the Singularity image format's peculiarities, Wave's freeze mode is mandatory when provisioning Singularity images.
:::

[singularity]: ./nextflow/use-cases.md#build-singularity-containers

### Deploying containers across multi-clouds

Cloud vendors provide integrated container registries with better performance and cost-efficiency than central, remote registries.
Storing container images in a private registry also enhances security and provide faster access with greater control.
Wave mirroring addresses these needs by copying containers to your chosen registry while preserving the original manifest, image name, and hash, and ensuring images remain unmodified and accessible via the original build hash.

### Container security scanning

Builds for OCI-compliant container images are automatically scanned for known security vulnerabilities. Wave conducts a vulnerability scan using the [Trivy](https://trivy.dev/) security scanner. Seqera Platform customers receive an email that includes a link to the security report listing any vulnerabilities discovered.

### Optimize workloads for specific architectures

Modern data pipelines can be deployed across different data centers having different hardware architectures such as AMD64, ARM64, and others. This requires curating different collections of containers for each architecture.
Wave allows for the on-demand provisioning of containers, depending on the target execution platform (in development).

### Near caching

The deployment of production pipelines at scale often requires the use of multiple cloud regions to enable efficient resource allocation.
However, this can result in an increased overhead when pulling container images from a central container registry. Wave allows the transparent caching of container images in the same region where computation occurs, reducing data transfer costs and time (in development).

### Wave Lite

Wave can be used in [Lite](./wave-lite.md) mode for container augmentation and inspection capabilities. Wave Lite can be deployed in both Docker Compose and Kubernetes installations.
