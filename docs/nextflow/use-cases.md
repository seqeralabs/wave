---
title: Use cases
description: Learn how to use Wave with Nextflow for container management, building, and security scanning
tags: [nextflow, wave, use cases]
---

With Nextflow and Wave, you can build, upload, and manage the container images required by your data analysis workflows automatically and on-demand during pipeline execution.

The following sections describe several common use cases.

:::tip
To get started with an example Nextflow pipeline that uses Wave, see [Nextflow and Wave](../tutorials/nextflow-wave.mdx).
:::

:::note
Nextflow integration with Wave requires Nextflow 22.10.0 or later.
:::

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

Replace `<PLATFORM_WORKSPACE_ID>` with your Seqera workspace ID.

Wave pushes built containers to the default AWS ECR repository with the name `195996028523.dkr.ecr.eu-west-1.amazonaws.com/wave/build`.
Images in this repository are automatically deleted one week after they are pushed.

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

To provision container images on demand, add the container Dockerfile to the [module directory](https://www.nextflow.io/docs/latest/module.html#module-directory) where the pipeline process is defined.
When Wave is enabled, it automatically builds the container using the Dockerfile, uploads the container to the registry, and uses the container to execute the script defined in the process.

If a process declares a `container` directive, it takes precedence over the Dockerfile definition.

To use the Dockerfile in the module directory, even when a process uses a `container` directive, add the following to your Nextflow configuration file:

```groovy
wave.strategy = ['dockerfile','container']
```

:::warning
Wave does not support `ADD`, `COPY`, or other Dockerfile commands that access files in the host file system.
:::

</details>

### Build Conda-based containers

Wave can provision containers based on the [`conda` directive](https://www.nextflow.io/docs/latest/process.html#conda).
This allows you to use Conda packages in your pipeline, even on cloud-native platforms like AWS Batch and Kubernetes, which do not support the Conda package manager directly.

<details open>
<summary>**Build Conda-based containers**</summary>

To provision Conda package containers with Wave, define the `conda` requirements in your pipeline processes.
Ensure the process doesn't include a `container` directive or Dockerfile.

To prioritize `conda` over `container` directives and Dockerfiles, add the following to your Nextflow configuration:

```groovy
wave.strategy = ['conda']
```

For Nextflow 23.10.0 or later, the `conda-forge::procps-ng` package is automatically included in provisioned containers. This package includes the `ps` command.

To set the priority of Conda channels, add the following to your Nextflow configuration file:

```groovy
wave.strategy = ['conda']
conda.channels = '<CONDA_CHANNELS>'
```

Replace `<CONDA_CHANNELS>` with a comma-separated list of your channel priorities. For example, `seqera,conda-forge,bioconda,defaults`.

</details>

### Build Singularity containers

Nextflow can build Singularity native images on demand using a `Singularityfile` or Conda packages.
Images are uploaded to an OCI-compliant container registry of your choice and stored as an [ORAS artifact](https://oras.land/).

:::note
Requires Nextflow version 23.09.0-edge or later and a version of Singularity (or Apptainer) that supports pulling images using the `oras:` pseudo-protocol.
:::

<details open>
<summary>**Build Singularity containers**</summary>

To enable provisioning of Singularity images in your pipeline, add the following to your Nextflow configuration file:

```groovy
singularity.enabled = true
wave.freeze = true
wave.strategy = ['conda']
wave.build.repository = '<BUILD_REPOSITORY>'
```

Replace `<BUILD_REPOSITORY>` with the repository where your Singularity image files should be uploaded.

When using a private repository, provide repository access keys via the Platform credentials manager. See [Credentials overview](https://docs.seqera.io/platform-cloud/credentials/overview) for more information.

You must grant access to the repository on the compute nodes. To grant access to the repository on compute nodes, run the following command:

```bash
singularity remote login <REMOTE_NAME>
```

Replace `<REMOTE_NAME>` with your Singularity remote endpoint.

See the [Singularity remote login documentation](https://docs.sylabs.io/guides/3.1/user-guide/cli/singularity_remote_login.html) for more information.

:::note
To build Singularity native images, disable both `singularity.ociAutoPull` and `singularity.ociMode` in your Nextflow configuration file. For more information, see the [Nextflow configuration documentation](https://www.nextflow.io/docs/latest/config.html#config-singularity).
:::

</details>

### Mirror containers across registries

Wave enables mirroring by copying containers used by your pipeline to a container registry of your choice.
Your pipeline can then pull containers from the target registry instead of the original registry.

<details open>
<summary>**Mirror containers across registries**</summary>

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
You must provide credentials through the Seqera Platform credentials manager to allow pushing containers to the build repository. See [Nextflow and Wave](../tutorials/nextflow-wave.mdx) for a detailed guide.
:::

</details>

### Security scan containers

Wave scans containers used in your Nextflow pipelines for security vulnerabilities. This feature helps you ensure that your workflows use secure container images by identifying potential security risks before and during pipeline execution.

<details open>
<summary>**Security scan containers**</summary>

To enable container security scanning, add the following to your Nextflow configuration file:

```groovy
wave.enabled = true
wave.scan.mode = 'required'
tower.accessToken = '<TOWER_ACCESS_TOKEN>'
```

Replace `<TOWER_ACCESS_TOKEN>` with your Seqera access token.

You can control which vulnerability levels are acceptable by specifying allowed levels:

```groovy
wave.scan.allowedLevels = 'low,medium'
```

The accepted vulnerability levels are: `low`, `medium`, `high`, and `critical`.

When you set `wave.scan.mode` to `required`, Wave blocks pipeline execution if containers contain vulnerabilities above the specified threshold.
The scanning uses the [Common Vulnerabilities Scoring System (CVSS)](https://en.wikipedia.org/wiki/Common_Vulnerability_Scoring_System) to assess security risks.

:::note
Scan results expire after seven days. When a container is accessed after this period, Wave automatically re-scans it to ensure up-to-date security assessments.
:::

</details>

### Use Wave with Fusion

Wave containers allow you to run your containerized workflow with the Fusion file system.

<details open>
<summary>**Use Wave with Fusion**</summary>

Wave with Fusion enables you to use an object storage bucket, such as AWS S3 or Google Cloud Storage, as your pipeline work directory.
This integration simplifies operations and improves performance on local, AWS Batch, Google Batch, and Kubernetes executions.

To enable Wave with Fusion, add the following to your Nextflow configuration file:

```groovy
wave.enabled = true
fusion.enabled = true
tower.accessToken = '<TOWER_ACCESS_TOKEN>'
```

Replace `<TOWER_ACCESS_TOKEN>` with your Seqera access token.

For more information about Fusion capabilities and configuration options, see the [Fusion file system documentation](https://docs.seqera.io/fusion).

</details>
