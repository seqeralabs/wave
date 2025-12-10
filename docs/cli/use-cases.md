---
title: Use cases
description: Learn how to use Wave CLI for building Docker and Singularity containers from various sources
date: "2024-08-22"
tags: [wave cli, use cases, containers, docker, singularity]
---

The Wave CLI enables you to build Docker and Singularity containers from various sources, including Dockerfiles, Singularity definition files, file system directories, and Conda packages. The following sections describe several common use cases.

:::tip
To get started with an example Nextflow pipeline that uses Wave CLI, see [Wave CLI][start].
:::

## Augment a container with a directory

The Wave CLI supports container augmentation with a specified directory. You can use container augmentation to dynamically add layers, such as scripts or configuration files, to your container.

<details open>
<summary>**Augment a container with a directory**</summary>

**Related CLI arguments**

Directory builds support the following arguments:

- `--layer`: Specifies a directory containing layer content.
- `--image`, `-i`: Specifies an existing container image (default: `docker.io`). Accepts image names (e.g., `alpine:latest`) or image URLs (e.g., `public.ecr.aws/docker/library/busybox`).

**Limitations**

The following limitations apply:

- A base image must be specified.
- Each file must be no larger than 1 MB.
- A directory must be no larger than 10 MB, inclusive of all files.

**Example usage**

Create a custom layer with a shell script and add it to an Alpine base image:

1. Create a new context directory:

    ```bash
    mkdir -p new-layer/usr/local/bin
    printf 'echo Hello world!' > new-layer/usr/local/bin/hello.sh
    chmod +x new-layer/usr/local/bin/hello.sh
    ```

1. Build and run the image:

    ```bash
    docker run $(wave -i alpine --layer new-layer) sh -c hello.sh
    ```
</details>

## Build a container from Conda packages

The Wave CLI supports building a container from a list of [Conda][conda] packages.

<details open>
<summary>**Build a container from Conda packages**</summary>

**Related CLI arguments**

Conda builds support the following arguments:

- `--conda-base-image`: Specifies the base image for installing Conda packages (default: `mambaorg/micromamba:1.5.10-noble`).
- `--conda-channels`: Specifies one or more comma-separated channels (default: `seqera,bioconda,conda-forge,defaults`).
- `--conda-file`: Specifies a [Conda lock file][conda-lock] path or URL.
- `--conda-package`: Specifies Conda packages to install. Supports expressions such as `bioconda::samtools=1.17` or `samtools>=1.0,<1.17`. Accepts a comma-separated list or can be specified multiple times.
- `--conda-run-command`: Specifies a Docker `RUN` command to execute during the build. Can be specified multiple times.
- `--build-template`: Specifies the build template to use. See [Build templates](#build-templates) for more details.

**Example usage**

Build a container with specific versions of the `samtools` and `bamtools` packages:

```bash
wave \
  --conda-package bamtools=2.5.2 \
  --conda-package samtools=1.17
```
</details>

## Build templates

Wave supports different build templates for creating container images from Conda packages. Build templates control how packages are installed and how the final container image is structured.

<details open>
<summary>**Build a container using a specific build template**</summary>

**Available templates**

| Template | Description |
|----------|-------------|
| (default) | Standard Micromamba v1 single-stage build. The final image includes the package manager. |
| `conda/micromamba/v2` | Multi-stage build using Micromamba 2.x. Produces smaller images without the package manager in the final stage. |
| `conda/pixi/v1` | Multi-stage build using [Pixi][pixi] package manager. Produces smaller images with faster dependency resolution. |

**Related CLI arguments**

Build template selection supports the following argument:

- `--build-template`: Specifies the build template to use for container builds.

**Benefits of multi-stage templates**

Multi-stage build templates (`conda/micromamba/v2` and `conda/pixi/v1`) offer several advantages:

- **Smaller images**: Build tools and package managers are excluded from the final image (30-50% size reduction typical).
- **Reproducibility**: Lock files are generated for exact package version tracking.
- **Security**: Fewer binaries in the final image reduces the attack surface.

**Singularity build requirements**

When building Singularity containers (`--singularity`) with the `conda/micromamba/v2` or `conda/pixi/v1` templates, base images must have the `tar` utility installed. This is required because Singularity's multi-stage builds use `proot`, which cannot copy directory structures directly between build stages. The templates work around this by compressing the environment with `tar` and extracting it in the final stage.

Docker builds are not affected by this requirement.

**Example usage**

Build a container using the Pixi template:

```bash
wave \
  --conda-package bamtools=2.5.2 \
  --build-template conda/pixi/v1
```

Build a container using the Micromamba v2 multi-stage template:

```bash
wave \
  --conda-package samtools=1.17 \
  --conda-package bwa=0.7.15 \
  --build-template conda/micromamba/v2
```

Build a Singularity container using Pixi:

```bash
wave \
  --conda-package numpy \
  --conda-package pandas \
  --build-template conda/pixi/v1 \
  --singularity \
  --freeze \
  --build-repo docker.io/user/repo
```

</details>

## Build a container from a Dockerfile

The Wave CLI supports building a container from a `Dockerfile`. Specifying an optional build context allows the use of `ADD` and `COPY` commands in a Dockerfile.

:::note
Dockerfile builds that require `--build-arg` for build time variables aren't currently supported.
:::

<details open>
<summary>**Build a container from a Dockerfile**</summary>

**Related CLI arguments**

Container builds support the following arguments:

- `--containerfile`, `-f`: A Dockerfile to build. Build arguments aren't currently supported.
- `--context`: A directory that contains the context for the build.

**Example usage**

Build a container that installs several packages from a `Dockerfile`:

1. Create a Dockerfile:

    ```bash
    cat << EOF > ./Dockerfile
    FROM alpine

    RUN apk update && apk add bash cowsay \
            --update-cache \
            --repository https://alpine.global.ssl.fastly.net/alpine/edge/community \
            --repository https://alpine.global.ssl.fastly.net/alpine/edge/main \
            --repository https://dl-3.alpinelinux.org/alpine/edge/testing
    EOF
    ```

1. Build and run the container:

    ```
    container=$(wave --containerfile ./Dockerfile)
    docker run --rm $container cowsay "Hello world"
    ```

Build a container from a Dockerfile with a local build context:

1. Create a Dockerfile that references a local file:

    ```bash
    cat << EOF > ./Dockerfile
    FROM alpine
    ADD hello.sh /usr/local/bin/
    EOF
    ```

1. Create the shell script in the build context directory:

    ```bash
    mkdir -p build-context/
    printf 'echo Hello world!' > build-context/hello.sh
    chmod +x build-context/hello.sh
    ```

1. Build and run the container with the build context:

    ```bash
    docker run $(wave -f Dockerfile --context build-context) sh -c hello.sh
    ```
</details>

## Build a Singularity container

The Wave CLI supports building [Singularity][singularity] containers. A target build repository, specified with the `--build-repo` argument, is required to build Singularity containers.

<details open>
<summary>**Build a Singularity container**</summary>

**Related CLI arguments**

Singularity container builds support the following arguments:

- `--build-repo`: Specifies the target repository to save the built container.
- `--freeze`: Enables container freeze mode.
- `--singularity`, `-s`: Enables Singularity container builds.
- `--tower-token`: Specifies a Seqera access token to access private registry credentials stored in Platform (not required if the `TOWER_ACCESS_TOKEN` environment variable is set).
- `--tower-workspace-id`: Specifies a Seqera workspace ID (e.g., `1234567890`) where credentials are stored. Requires `--tower-token` flag or `TOWER_ACCESS_TOKEN` environment variable to be set.

**Limitations**

The following limitations apply:

- The `linux/arm64` platform is not currently supported.

**Example usage**

Augment a Docker base image and save it as a Singularity container:

```bash
wave -i alpine --layer context-dir/ --build-repo docker.io/user/repo
```

Build a Singularity container from a SingularityCE definition (`.def`) file:

```bash
wave -f hello-world.def --singularity --freeze --build-repo docker.io/user/repo
```

Build a Singularity container from Conda packages:

```bash
wave --conda-package bamtools=2.5.2 --conda-package samtools=1.17 --freeze --singularity --build-repo docker.io/user/repo
```

</details>

## Build a container and freeze to a container registry

The Wave CLI supports building a container and persisting the container to a container registry, such as DockerHub. You can refer to this frozen container image in a Dockerfile or [Nextflow][nextflow] pipeline in the same way as any other container.

<details open>
<summary>**Build a container and freeze to a container registry**</summary>

**Prerequisites**

Ensure the following conditions are met:

- You created a Seqera access token
- You specified the destination container registry credentials in Seqera
- You specified the Seqera access token via either the `TOWER_ACCESS_TOKEN` environment variable or the `--tower-token` Wave command-line option

**Related CLI arguments**

Container freeze builds support the following arguments:

- `--build-repo`: Specifies the target repository to save the built container.
- `--freeze`: Enables container freeze mode.
- `--tower-token`: Specifies a Seqera access token for accessing private registry credentials (not required if the `TOWER_ACCESS_TOKEN` environment variable is set).
- `--tower-workspace-id`: Specifies a Seqera workspace ID (e.g., `1234567890`) where credentials are stored.

**Example usage**

Freeze the `alpine` container image to a private Docker Hub registry:

```bash
wave -i alpine --freeze \
  --build-repo docker.io/<USER>/repo --tower-token <TOWER_TOKEN>
```

</details>

## Mirror a container image to another registry

The Wave CLI supports mirroring, i.e., copying containers to a container registry of your choice.

<details open>
<summary>**Mirror a container image to another registry**</summary>

**Prerequisites**

Ensure the following conditions are met:

- You created a Seqera access token.
- You specified the destination container registry credentials in Seqera.
- You specified the Seqera access token via either the `TOWER_ACCESS_TOKEN` environment variable or the `--tower-token` Wave command-line option.

**Related CLI arguments**

Container mirroring supports the following arguments:

- `--mirror`: Enables container mirror mode.
- `--build-repo`: Specifies the target repository to save the mirrored container.
- `--tower-token`: Specifies a Seqera access token to access private registry credentials stored in Platform (not required if the `TOWER_ACCESS_TOKEN` environment variable is set).

**Example usage**

Mirror the [`samtools:0.1.16--2`][samtools] container image to a private Docker Hub registry:

```bash
wave -i quay.io/biocontainers/samtools:0.1.16--2 --mirror \
  --build-repo docker.io/<USER>/containers --tower-token <TOWER_TOKEN>
```

</details>

[conda]: https://anaconda.org/anaconda/repo
[conda-lock]: https://github.com/conda/conda-lock
[nextflow]: https://www.nextflow.io/
[pixi]: https://pixi.sh/
[samtools]: https://quay.io/repository/biocontainers/samtools?tab=tags
[singularity]: https://docs.sylabs.io/guides/latest/user-guide/introduction.html
[singularityce]: https://docs.sylabs.io/guides/latest/user-guide/definition_files.html
[start]: /wave_docs/wave_repo/docs/tutorials/wave-cli.mdx
