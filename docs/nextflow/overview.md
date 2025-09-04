---
title: Nextflow integration
---

You can use Wave directly from your Nextflow pipelines. Nextflow integration with Wave supports private repositories, container freezing, and conda packages.

:::note
Nextflow integration with Wave requires Nextflow 22.10.0, or later.
:::

## Get started

To enable Wave in your Nextflow pipeline, add the following to your Nextflow configuration file:

```groovy
wave.enabled = true
tower.accessToken = '<TOWER_ACCESS_TOKEN>'
```

Replace `<TOWER_ACCESS_TOKEN>` with your Seqera access token.

Using a Seqera access token is optional but provides additional capabilities:

- Access to private repositories
- Higher API request limits than anonymous users

For all Nextflow configuration options, see [Configuration options](#configuration-options).

## Use Wave features with Nextflow

The following sections describe how to use Wave features with Nextflow.

### Access private container repositories

Use Wave to access private repositories in your Nextflow pipelines. Provide your repository access keys as [Seqera Platform credentials](https://docs.seqera.io/platform/latest/credentials/overview).

<details open>
<summary>**Access private container repositories**</summary>

To access private container repositories, add your [Seqera access token](https://docs.seqera.io/platform/latest/api/overview#authentication) to your Nextflow configuration file.

```groovy
tower.accessToken = '<TOWER_ACCESS_TOKEN>'
```

Replace `<TOWER_ACCESS_TOKEN>` with your Seqera access token.

If you created credentials in an organization workspace, also add your workspace ID:

```groovy
tower.workspaceId = '<PLATFORM_WORKSPACE_ID>'
```

Replace `<PLATFORM_WORKSPACE_ID>` with your Platform organization workspace ID.

Wave uploads built containers to the default AWS ECR repository with the name `195996028523.dkr.ecr.eu-west-1.amazonaws.com/wave/build`.
Images in this repository are deleted automatically one week after they are pushed.

To store Wave containers in your own container repository, add the following to your Nextflow configuration file:

```groovy
wave.build.repository = '<BUILD_REPOSITORY>'
wave.build.cacheRepository = '<CACHE_REPOSITORY>'
```

Replace the following:

- `<BUILD_REPOSITORY>`: the repository to store your built container images
- `<CACHE_REPOSITORY>`: the repository to store image layers for caching

</details>

### Build Nextflow module containers

Wave can build and provision container images on demand for your Nextflow pipelines.

<details open>
<summary>**Build Nextflow module containers**</summary>

To provision container images on demand, add the Dockerfile of the container to the [module directory](https://www.nextflow.io/docs/latest/module.html#module-directory) where the pipeline process is defined.
When Wave is enabled, it automatically uses the Dockerfile to build the required container, uploads the container to the registry, and uses the container to execute the script defined in the process.

:::note
If a process declares a `container` directive, it takes precedence over the Dockerfile definition.
:::

To use the Dockerfile in the module directory, even when a process uses a `container` directive, add the following to your Nextflow configuration file:

```groovy
wave.strategy = ['dockerfile','container']
```

:::warning
Wave does not support `ADD`, `COPY`, or other Dockerfile commands that access files in the host file system.
:::

</details>

### Build conda-based containers

Wave can provision containers based on the [`conda` directive](https://www.nextflow.io/docs/latest/process.html#conda).
This allows you to use conda packages in your pipeline, even on cloud-native platforms like AWS Batch and Kubernetes, which do not support the conda package manager directly.

<details open>
<summary>**Build conda-based containers**</summary>

Define the `conda` requirements in your pipeline processes.
Ensure the process doesn't include a `container` directive or Dockerfile.

To prioritize `conda` over `container` directives and Dockerfiles, add the following to your Nextflow configuration:

```groovy
wave.strategy = ['conda']
```

Replace `<TOWER_ACCESS_TOKEN>` with your Seqera access token.

For Nextflow 23.10.x, or later, the `conda-forge::procps-ng` package is included automatically in provisioned containers. This package includes the `ps` command.

You can set conda channels and their priority with `conda.channels`:

```groovy
wave.strategy = ['conda']
conda.channels = 'seqera,conda-forge,bioconda,defaults'
```

</details>

### Build Singularity containers

Nextflow can build Singularity native images on demand using a `Singularityfile` or conda packages.
Images are uploaded to an OCI-compliant container registry of your choice and stored as an [ORAS artifact](https://oras.land/).

:::note
Available as of Nextflow version 23.09.0-edge.
:::

<details open>
<summary>**Build Singularity containers**</summary>

:::note
This feature requires a version of Singularity (or Apptainer) that supports pulling images using the `oras:` pseudo-protocol.
:::

To enable provisioning of Singularity images in your pipeline, add the following to your Nextflow configuration file:

```groovy
singularity.enabled = true
wave.freeze = true
wave.strategy = ['conda']
wave.build.repository = '<BUILD_REPOSITORY>'
```

Replace `<BUILD_REPOSITORY>` with the repository where your Singularity image files should be uploaded.

When using a private repository, provide repository access keys via the Platform credentials manager. See [Authenticate private repositories](https://docs.seqera.io/platform/24.1/credentials/overview) for more information.

The access to the repository must be granted in the compute nodes. To grant access to the repository on compute nodes, run the following command:

```bash
singularity remote login <REMOTE_NAME>
```

Replace `<REMOTE_NAME>` with your Singularity remote endpoint.

See the [Singularity remote login documentation](https://docs.sylabs.io/guides/3.1/user-guide/cli/singularity_remote_login.html) for more information.

:::note
To build Singularity native images, disable both `singularity.ociAutoPull` and `singularity.ociMode` in your Nextflow configuration file. For more information, see the Nextflow [configuration documentation](https://www.nextflow.io/docs/latest/config.html#config-singularity).
:::

</details>

### Mirror containers across registries

Wave enables mirroring, i.e., copying containers used by your pipeline to a container registry of your choice.
Your pipeline can then pull containers from the target registry instead of the original registry.

<details open>
<summary>**Build Singularity containers**</summary>

To enable mirroring, add the following to your Nextflow configuration file:

```groovy
wave.enabled = true
wave.mirror = true
wave.build.repository = '<BUILD_REPOSITORY>'
tower.accessToken = '<TOWER_ACCESS_TOKEN>'
```

Replace the following:

- `<BUILD_REPOSITORY>`: the repository to store your built containers
- `<TOWER_ACCESS_TOKEN>`: your Seqera access token

:::note
You must provide credentials through the Seqera Platform credentials manager to allow pushing containers to the build repository. See [Nextflow and Wave](./tutorials/nextflow-wave.mdx) for a detailed guide.
:::

</details>

### Use Wave with Fusion

Wave containers allow you to run your containerized workflow with the Fusion file system.

<details open>
<summary>**Use Wave with Fusion**</summary>

Wave with Fusion allows you to use an object storage bucket, such as AWS S3 or Google Cloud Storage, as your pipeline work directory.
This can simplify and speed up operations on local, AWS Batch, Google Batch, or Kubernetes executions.

For more information, see:
- [Fusion documentation](https://docs.seqera.io/fusion)
- [Nextflow Fusion integration documentation](https://www.nextflow.io/docs/latest/fusion.html)

</details>


## Limitations

### Use of SHA256 digest in the image name

Wave does not support using an SHA256 digest in the image name (for example, `ubuntu@sha256:3235...ce8f`) when using the augmentation process to extend container images.

To reference a container by SHA256 digest in the image name with Wave, enable freeze mode and force the creation of a new container image using your specified base image.

Add the following to your Nextflow configuration file:

```groovy
wave.enabled = true
wave.freeze = true
wave.strategy = ['dockerfile']
wave.build.repository = '<BUILD_REPOSITORY>'
```

Replace `<BUILD_REPOSITORY>` with the repository where your image files should be uploaded.
