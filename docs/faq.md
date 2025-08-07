---
title: Frequently asked questions
---

## Is Wave a container registry?

Wave implements the Docker registry pull API, and therefore allows Docker and other compatible container engines to pull container images from it. However, strictly speaking it's not a container registry because it does not store container images, and it's not possible to push an image into it.

From a technical point of view, Wave behaves as a proxy server that intermediates container pull requests from the Docker client and the target registry where the container image is stored.

## Why I should use Wave instead of a container registry?

Wave has been designed to streamline the use of software containers with Nextflow data analysis pipelines, in three ways:

1. Safely handle the authentication of container repositories to allow access to private registries and the ability to pull from public registries without being affected by service rate limits.
2. Dynamically include in the container execution context pipeline scripts and infrastructure-related dependencies, without the need to rebuild the corresponding containers.
3. Building the pipeline containers on-demand, using Dockerfiles associated with the pipeline modules or Conda packages specified in the pipeline configuration.

## How I can specify the credentials of my private container registry when using Wave?

You'll need to store the container registry credentials in your [Seqera Platform](https://tower.nf/) account.

If you launch the Nextflow pipeline with Seqera, there's nothing else to do (other than using a compute environment with Wave support enabled).
If you're launching the pipeline with the Nextflow command line, provide your Seqera Platform access token in the Nextflow configuration file adding the setting `tower.accessToken='<YOUR TOKEN>'`.

## Does Wave modify my container images?

No. Wave doesn't alter or modify your container images. Wave acts as proxy server between the Docker client (or equivalent container engine) and the target registry where the container image is hosted.

With Wave container augmentation, you can include extra content required by the pipeline execution when you pull container images.

## Can the container built by Wave be stored in my container registry?

Yes. The container registry where the container built by Wave can be specified by adding the following setting in your pipeline Nextflow config file:

```
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
- [Google Artifactory Registry](https://cloud.google.com/artifact-registry)
