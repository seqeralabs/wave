---
title: How container provisioning works
---

In the container lifecycle, images are generally created (*built*) and uploaded (*pushed*) to a container registry, and then these images are downloaded (*pulled*) for the execution of a specific pipeline.

The Wave container provisioning process streamlines the container lifecycle by delivering container images on-demand during the pipeline execution and making sure each container includes the dependencies required by the requesting actor, such as a pipeline task or a user.

Wave provides the following container provisioning capabilities:

- Container augmentation
- Container freezing

## Container augmentation

The container augmentation provisioning mode allows _extending_ the content of a container image without rebuilding it. Instead, this mechanism modifies a container image during the pull phase made by a Docker-compatible client. Augmented containers are ephemeral: they are not stored in a container repository, and they can only be accessed for a short period of time. The extended content added by Wave is served from a CDN. The augmentation process does not perform any _build_ operation behind the scenes.

This approach supports use cases such as the following:

- Authenticate access to your private repositories with Seqera Platform credentials
- Extend existing containers by adding infrastructure and pipeline dependencies on the fly, without rebuilding and maintaining additional container images

Container augmentation works as follows:

1. The client, either Nextflow or Wave CLI, submits a container request specifying:
   1. The Seqera Platform user identity
   1. The container image to be augmented
   1. The container extension configuration, which can be either a custom payload, one or more extension layers, or container images.
1. The Wave service validates the request and authorizes the user submitting a request to the Platform service.
1. The Wave service responds with an ephemeral container image name e.g. `wave.seqera.io/wt/<id_token>/library/alpine:latest`. The `<id_token>` is uniquely assigned and is used to identify and authorize the following container request.
1. The Docker client uses the returned image name to pull the container binary content of the upstream image directly from the target registry.
1. The content added by Wave as one or more layer extensions is shipped by the Wave service.

Notable parts of this workflow include:

- Wave acts as a proxy between the Docker client and the target registry that hosts the container image.
- Wave modifies, if needed, the container manifest to add the new content as specified by the request, but it does not (and cannot) alter the container layer blob files that have a unique checksum, which is preserved.
- Image blobs are downloaded directly from the target registry, not from Wave.

## Container freezes

The container _freeze mode_ allows the provisioning of non-ephemeral containers that are stored permanently in a container registry of your choice. When using the freeze mode, the Wave service transparently carries out a regular container build.

This approach supports use cases such as the following:

- Create container images on-demand from Conda packages
- Deliver multi-architecture (AMD64 and ARM64) and multi-format (Docker and Singularity) container collections
- Deliver container images in the same region where compute is performed

Wave freeze mode works as follows:

1. The client, either Nextflow or the Wave CLI, submits a container request specifying:
   1. The Seqera Platform user identity
   1. The container image to augment
   1. The container extension configuration, which can be either a custom payload, one or more extension layers, or container images
   1. The target repository where the built container should be uploaded
1. The Wave service validates the request and authorizes the user via a request to the Platform service.
1. The Wave service checks if the container image already exists in the target registry.
1. If the image does not exist, Wave launches a container build job and pushes the resulting image to the target registry.
1. The Wave service responds with the container image name e.g. `example.com/some/image/build:1234567`.

Notable parts of this workflow include:

- Container images provisioned with freeze mode are regular container builds.
- Each container image is associated with a unique ID that is obtained by hashing the following elements:
  - The Container file
  - Any package dependencies
  - The target platform, which is either AMD64 or ARM64
  - The target repository name
- When a request for the same container is made, the same ID is assigned to it and therefore, the build is skipped.
- The resulting images are hosted in your selected repository and not cached locally, unless a cache repository is specified.
- The container images are stored permanently unless the repository owner deletes them.

## Container provisioning capability matrix

Wave supports the following types of container builds:

|Type|Provisioning mode|Source|Freeze|Build repo|Accessibility|Format|
|--- |--- |--- |--- |--- |--- |--- |
|Ephemeral|Augmentation|Container image|No|n/a|Temporary token|Docker|
|Ephemeral|Build|Container file|No|Default|Temporary token|Docker|
|Ephemeral|Build|Conda package|No|Default|Temporary token|Docker|
|Ephemeral|Build|Container file|No|Custom|Temporary token|Docker|
|Ephemeral|Build|Conda package|No|Custom|Temporary token|Docker|
|Durable|Build|Container file|Yes|Custom|Docker auth|Docker /Singularity|
|Durable|Build|Conda package|Yes|Custom|Docker auth|Docker /Singularity|
|Community (durable)|Build|Container file|Yes|Default|Public|Docker /Singularity|
|Community (durable)|Build|Conda package|Yes|Default|Public|Docker /Singularity|
