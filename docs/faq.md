---
title: FAQ
description: Frequently asked questions about Wave containers
hide:
    - navigation
    - footer
---

# Frequently Asked Questions

### Is Wave a container registry?

Wave implements the Docker registry pull API, and therefore allows Docker and other compatible
container engines to pull container images from it. However, strictly speaking it's not a container registry
because it does not store container images, and it's not possible to push image into it.

From a technical point of view Wave behave a proxy server that intermediates container pull requests from
the Docker client and the target registry where the container image is stored.

### Why I should use Wav instead of a container registry?

Wave has been designed to streamline the use of software containers with Nextflow data analysis pipelines, in three ways:

1. safely handle the authentication of container repositories to allow the access of private registries and the pull from public registries without being affected by service rate limits.
2. dynamically include in the container execution context pipeline scripts and infrastructure related dependencies, without requiring to rebuild the corresponding containers.
3. building the pipeline containers on-demand, using Dockerfiles associated with the pipeline modules or Conda packages specified in the pipeline configuration.

### How I can specify the credentials of my private container registry when using Wave?

You will need to store the container registry credentials in your [Tower](https://tower.nf/) account.

If you are launching the Nextflow pipeline with Tower, there's nothing else to do (other than using a compute environment that has the Wave support enabled).
If you are launching the pipeline with the Nextflow command line, provide your Tower access token in the Nextflow configuration
file adding the setting `tower.accessToken='<YOUR TOKEN>'`.

### Does Wave modify my container images?

No. Wave does alter or modify your container images. Wave acts as proxy server
in between the Docker client (or equivalent container engine) and the target registry
from where the container image is hosted.

The container image when pulled from Wave can however include some extra content,
as required by the pipeline execution, by using what's called container augmentation.

### Can the container built by Wave be stored in my container registry?

Yes. The container registry where the container built by Wave can be specified
by adding the following setting in your pipeline Nextflow config file:

```
wave.build.repository = 'example.com/your/build-repo'
```

### Which container registries are supported by Wave?

Wave has been tested with the following container registries:

-   [AWS Elastic Container Registry](https://aws.amazon.com/ecr/)
-   [Azure Container Registry](https://azure.microsoft.com/en-us/products/container-registry)
-   [Docker Hub](https://hub.docker.com/)
-   [Quay.io](https://quay.io/)
-   [Gitea Container Registry](https://docs.gitea.io/en-us/packages/container/)
-   [GitHub Container Registry](https://github.blog/2020-09-01-introducing-github-container-registry/)
-   [Google Artifactory Registry](https://cloud.google.com/artifact-registry)
