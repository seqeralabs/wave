---
title: Use cases
description: Learn how to use Wave with Nextflow for container management, building, and security scanning
date: "2024-08-22"
last_update: "2025-10-16"
tags: [nextflow, wave, use cases]
---

With Nextflow and Wave, you can build, upload, and manage the container images automatically and on demand during pipeline execution.
The following sections describe several common use cases.

:::tip
To get started with an example Nextflow pipeline that uses Wave, see [Nextflow and Wave](../tutorials/nextflow-wave.mdx).
:::

:::note
Nextflow integration with Wave requires Nextflow 22.10.0 or later.
:::

## Access private container repositories

Use Wave to access private repositories for your Nextflow pipelines.

<details open>
<summary>**Access private container repositories**</summary>

To enable private container repository access:

1. Add the following to your Nextflow configuration:

    ```groovy
    wave.enabled = true
    tower.accessToken = '<TOWER_ACCESS_TOKEN>'
    ```

    Replace `<TOWER_ACCESS_TOKEN>` with your [Seqera access token](../tutorials/nextflow-wave.mdx#create-your-seqera-access-token).

1. (Optional) If you created your credentials in an organization workspace, add your workspace ID to your Nextflow configuration:

    ```groovy
    tower.workspaceId = '<TOWER_WORKSPACE_ID>'
    ```

    Replace `<TOWER_WORKSPACE_ID>` with your Seqera workspace ID.

1. Configure your repository access in Seqera. See [Seqera Platform credentials](https://docs.seqera.io/platform/latest/credentials/overview) for more information.

1. Add your build and build cache repository to your Nextflow configuration:

    ```groovy
    wave.build.repository = '<BUILD_REPOSITORY>'
    wave.build.cacheRepository = '<CACHE_REPOSITORY>'
    ```

    Replace the following:

    - `<BUILD_REPOSITORY>`: the repository to store your built container images
    - `<CACHE_REPOSITORY>`: the repository to store image layers for caching

</details>

## Build Nextflow module containers

Wave can build and provision container images on demand for your Nextflow pipelines.

<details open>
<summary>**Build Nextflow module containers**</summary>

To enable Wave to build Nextflow module containers:

1. Add your Dockerfile to the [module directory](https://www.nextflow.io/docs/latest/module.html#module-directory) where you define the pipeline process.

1. Enable Wave in your Nextflow configuration:

    ```groovy
    wave.enabled = true
    ```

1. (Optional) Set your Wave strategy to prioritize Dockerfiles in your Nextflow configuration:

    ```groovy
    wave.strategy = ['dockerfile','container']
    ```

    :::note
    The `container` directive takes precedence over a Dockerfile by default.
    :::

:::warning
Wave does not support `ADD`, `COPY`, or other Dockerfile commands that access files in the host file system.
:::

</details>

## Build Conda-based containers

Wave can provision containers based on the [`conda` directive](https://www.nextflow.io/docs/latest/process.html#conda).
This allows you to use Conda packages in your pipeline, even on cloud-native platforms like AWS Batch and Kubernetes, which do not support the Conda package manager directly.

<details open>
<summary>**Build Conda-based containers**</summary>

To enable Wave to provision Conda package containers:

1. Define your Conda packages using the `conda` directive in your pipeline processes.

1. Enable Wave in your Nextflow configuration:

    ```groovy
    wave.enabled = true
    ```

1. (Optional) Set your Wave strategy to prioritize `conda` in your Nextflow configuration:

    ```groovy
    wave.strategy = ['conda']
    ```

    :::note
    The `container` directive or a Dockerfile takes precedence over the `conda` directive by default.
    :::

    :::info
    Nextflow 23.10.0 or later automatically includes the `conda-forge::procps-ng` package in provisioned containers. This package includes the `ps` command.
    :::

1. Set your Conda channel priority:

    ```groovy
    conda.channels = '<CONDA_CHANNELS>'
    ```

    Replace `<CONDA_CHANNELS>` with a comma-separated list of your channel priorities. For example, `seqera,conda-forge,bioconda,defaults`.

</details>

## Build Singularity containers

Nextflow can build Singularity native images on demand using a `Singularityfile` or Conda packages.
Images are uploaded to an OCI-compliant container registry of your choice and stored as an [ORAS artifact](https://oras.land/).

:::note
Requires Nextflow version 23.09.0-edge or later and a version of Singularity (or Apptainer) that supports pulling images using the `oras:` pseudo-protocol.
:::

<details open>
<summary>**Build Singularity containers**</summary>

To enable provisioning of Singularity images:

1. Add the following to your Nextflow configuration:

    ```groovy
    wave.enabled = true
    wave.freeze = true
    wave.strategy = ['conda']
    singularity.enabled = true
    ```


1. (Optional) To store your Singularity image files in a private registry:

    1. Configure your repository access in Seqera. See [Seqera Platform credentials](https://docs.seqera.io/platform-cloud/credentials/overview) for more information.

    1. Add your build repository to your Nextflow configuration:

        ```groovy
        wave.build.repository = '<BUILD_REPOSITORY>'
        ```

        Replace `<BUILD_REPOSITORY>` with your OCI-compliant container registry.


1. Grant access to the repository on compute nodes:

    ```bash
    singularity remote login <REMOTE_ENDPOINT>
    ```

    Replace `<REMOTE_ENDPOINT>` with your Singularity remote endpoint. See [Singularity remote login](https://docs.sylabs.io/guides/3.1/user-guide/cli/singularity_remote_login.html) for more information.

1. (Optional) To build Singularity native images, disable both `singularity.ociAutoPull` and `singularity.ociMode` in your Nextflow configuration. See [Nextflow configuration](https://www.nextflow.io/docs/latest/config.html#config-singularity) for more information.

</details>

## Mirror containers across registries

Wave enables mirroring by copying containers used by your pipeline to a container registry of your choice.
Your pipeline can then pull containers from the target registry instead of the original registry.

<details open>
<summary>**Mirror containers across registries**</summary>

To enable container mirroring:

1. Add the following to your Nextflow configuration:

    ```groovy
    wave.enabled = true
    wave.mirror = true
    tower.accessToken = '<TOWER_ACCESS_TOKEN>'
    ```

    Replace `<TOWER_ACCESS_TOKEN>` with your [Seqera access token](../tutorials/nextflow-wave.mdx#create-your-seqera-access-token).


1. Configure your private repository access in Seqera. See [Seqera Platform credentials](https://docs.seqera.io/platform-cloud/credentials/overview) for more information.

1. Add your build repository to your Nextflow configuration:

    ```groovy
    wave.build.repository = '<BUILD_REPOSITORY>'
    ```

    Replace `<BUILD_REPOSITORY>` with your container registry.

</details>

## Security scan containers

Wave scans containers used in your Nextflow pipelines for security vulnerabilities. This feature helps you ensure that your pipelines use secure container images by identifying potential security risks before and during pipeline execution.

<details open>
<summary>**Security scan containers**</summary>

To enable container security scanning:

1. Add the following to your Nextflow configuration:

        ```groovy
        wave.enabled = true
        wave.scan.mode = 'required'
        tower.accessToken = '<TOWER_ACCESS_TOKEN>'
        ```

    Replace `<TOWER_ACCESS_TOKEN>` with your [Seqera access token](../tutorials/nextflow-wave.mdx#create-your-seqera-access-token).

1. Add the acceptable vulnerability levels to your Nextflow configuration:

    ```groovy
    wave.scan.allowedLevels = 'low,medium'
    ```

    Accepted vulnerability levels include: `low`, `medium`, `high`, and `critical`.

:::note
When you set `wave.scan.mode` to `required`, Wave blocks pipeline execution if containers have vulnerabilities above the specified threshold.
The scanning uses the [Common Vulnerabilities Scoring System (CVSS)](https://en.wikipedia.org/wiki/Common_Vulnerability_Scoring_System) to assess security risks.
:::

:::note
Scan results expire after seven days. When a container is accessed after this period, Wave automatically re-scans it to ensure up-to-date security assessments.
:::

</details>

## Use Wave with Fusion

Wave containers allow you to run your containerized pipelines with the Fusion file system. Wave with Fusion enables you to use an object storage bucket, such as AWS S3 or Google Cloud Storage, as your pipeline work directory.

<details open>
<summary>**Use Wave with Fusion**</summary>

To enable Wave with Fusion, add the following to your Nextflow configuration:

    ```groovy
    wave.enabled = true
    fusion.enabled = true
    tower.accessToken = '<TOWER_ACCESS_TOKEN>'
    ```

    Replace `<TOWER_ACCESS_TOKEN>` with your [Seqera access token](../tutorials/nextflow-wave.mdx#create-your-seqera-access-token).

:::note
For more information about Fusion capabilities and configuration options, see the [Fusion file system documentation](https://docs.seqera.io/fusion).
:::

</details>
