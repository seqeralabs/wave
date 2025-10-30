---
title: Frequently asked questions
description: Find answers to common questions about Wave container provisioning
date: "2023-11-11"
tags: [wave, containers, nextflow, faq]
---

## Is Wave a container registry?

Wave implements the Docker registry pull API, and therefore allows Docker and other compatible container engines to pull container images from it. However, strictly speaking it's not a container registry because it does not store container images, and it's not possible to push an image into it.

From a technical point of view, Wave behaves as a proxy server that intermediates container pull requests from the Docker client and the target registry where the container image is stored.

## Why should I use Wave instead of a container registry?

Wave has been designed to streamline the use of software containers with Nextflow data analysis pipelines, in three ways:

1. Safely handle the authentication of container repositories to allow access to private registries and the ability to pull from public registries without being affected by service rate limits.
2. Dynamically include in the container execution context pipeline scripts and infrastructure-related dependencies, without the need to rebuild the corresponding containers.
3. Building the pipeline containers on-demand, using Dockerfiles associated with the pipeline modules or Conda packages specified in the pipeline configuration.

## How can I specify the credentials of my private container registry when using Wave?

Store the container registry credentials in your [Seqera Platform](https://cloud.seqera.io) account.

If you launch the Nextflow pipeline with Seqera, there's nothing else to do (other than using a compute environment with Wave support enabled).
If you're launching the pipeline with the Nextflow command line, add your [Seqera access token](https://docs.seqera.io/platform/latest/api/overview#authentication) to your Nextflow configuration. See [Access private container repositories](./nextflow/use-cases.md#access-private-container-repositories) for more information.

## Does Wave modify my container images?

No. Wave doesn't alter or modify your container images. Wave acts as proxy server between the Docker client (or equivalent container engine) and the target registry where the container image is hosted.

With Wave container augmentation, you can include extra content required by the pipeline execution when you pull container images.

## Can the container built by Wave be stored in my container registry?

Yes. You can specify the container registry where Wave stores built containers by adding the following setting in your Nextflow configuration:

```groovy
wave.build.repository = 'example.com/your/build-repo'
```

## Which container registries are supported by Wave?

Wave has been tested with the following container registries:

- [AWS Elastic Container Registry](https://aws.amazon.com/ecr/)
- [Azure Container Registry](https://azure.microsoft.com/en-us/products/container-registry)
- [Docker Hub](https://hub.docker.com/)
- [Quay.io](https://quay.io/)
- [Gitea Container Registry](https://docs.gitea.io/en-us/packages/container/)
- [GitHub Container Registry](https://github.blog/2020-09-01-introducing-github-container-registry/)
- [Google Artifact Registry](https://cloud.google.com/artifact-registry)

## Can I use SHA256 digests in image names with Wave?

Wave does not support using an SHA256 digest in the image name (for example, `ubuntu@sha256:3235...ce8f`) when using the augmentation process to extend container images.

To reference a container by SHA256 digest in the image name with Wave, enable freeze mode and force the creation of a new container image using your specified base image by adding the following to your Nextflow configuration:

```groovy
wave.enabled = true
wave.freeze = true
wave.strategy = ['dockerfile']
wave.build.repository = '<BUILD_REPOSITORY>'
```

Replace `<BUILD_REPOSITORY>` with the repository where your image files should be uploaded.
