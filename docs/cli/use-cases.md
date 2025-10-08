---
title: Use cases
---

With the Wave CLI you can build Docker and Singularity containers from a variety of sources, including a Dockerfile, Singularity def file, file system directory, and Conda packages.

The following sections describe several common use cases.

:::tip
To get started with an example Nextflow pipeline that uses Wave CLI, see [Wave CLI][start].
:::

## Augment a container with a directory

The Wave CLI supports container augmentation with a specified directory. You can use container augmentation to dynamically add a layer to your container, so you can inject scripts or configuration files as a new layer.

<details open>
<summary>**Augment a container with a directory**</summary>

The following limitations apply:

- A file must be no larger than 1 MB each.
- A directory must be no larger than 10 MB, inclusive of all files.
- A base image must be specified.

**Related CLI arguments**

The following arguments are used for a directory build:

- `--layer`: A directory that contains layer content.
- `--image` or `-i`: An existing container image. The default image registry is `docker.io`. Specify an image name such as `alpine:latest` or an image URL such as `public.ecr.aws/docker/library/busybox`.

**Example usage**

Create a new context directory:

```
mkdir -p new-layer/usr/local/bin
printf 'echo Hello world!' > new-layer/usr/local/bin/hello.sh
chmod +x new-layer/usr/local/bin/hello.sh
```

Use the CLI to build the image and run the result with Docker:

```
docker run $(wave -i alpine --layer new-layer) sh -c hello.sh
```
</details>

## Build a container from conda packages

The Wave CLI supports building a container from a list of [Conda][conda] packages.

<details open>
<summary>**Build a container from Conda packages**</summary>

**Related CLI arguments**

Conda builds support the following arguments:

- `--conda-base-image`: A base image for installing Conda packages. The default value is `mambaorg/micromamba:1.5.10-noble`.
- `--conda-channels`: One or more comma-separated channels. The default value is ` seqera,bioconda,conda-forge,defaults`.
- `--conda-file`: A [Conda lock file][conda-lock]. Can be a local file or a URL to a remote file.
- `--conda-package`: A Conda package to install. Can be specified multiple times. Expressions are supported, such as `bioconda::samtools=1.17` or `samtools>=1.0,<1.17`.
- ` --conda-run-command`: A Docker `RUN` command used when containers are built. Can be specified multiple times.

**Example usage**

In the following example, a container with the `samtools` and `bamtools` packages is built:

```
wave \
  --conda-package bamtools=2.5.2 \
  --conda-package samtools=1.17
```

</details>

## Build a container from a Dockerfile

The Wave CLI supports building a container from a `Dockerfile`. Specifying an optional build context allows the use of `ADD` and `COPY` commands in a Dockerfile.

:::note
Building a Dockerfile that requires `--build-arg` for build time variables isn't currently supported.
:::

<details open>
<summary>**Build a container from a Dockerfile**</summary>

**Related CLI arguments**

- `--containerfile` or `-f`: A Dockerfile to build. Build args aren't currently supported.
- `--context`: A directory that contains the context for the build.

**Example usage**

In the following example `Dockerfile`, several packages are installed:

```
cat << EOF > ./Dockerfile
FROM alpine

RUN apk update && apk add bash cowsay \
        --update-cache \
        --repository https://alpine.global.ssl.fastly.net/alpine/edge/community \
        --repository https://alpine.global.ssl.fastly.net/alpine/edge/main \
        --repository https://dl-3.alpinelinux.org/alpine/edge/testing
EOF
```

Build and run the container based on the Dockerfile in the previous example by running the following command:

```
container=$(wave --containerfile ./Dockerfile)
docker run --rm $container cowsay "Hello world"
```

In the following example `Dockerfile`, a local context is used:

```
cat << EOF > ./Dockerfile
FROM alpine
ADD hello.sh /usr/local/bin/
EOF
```

Create the shell script referenced in the previous example by running the following commands in your terminal:

```
mkdir -p build-context/
printf 'echo Hello world!' > build-context/hello.sh
chmod +x build-context/hello.sh
```

Build and run the container based on the Dockerfile in the previous example by running the following command:

```
docker run $(wave -f Dockerfile --context build-context) sh -c hello.sh
```
</details>

## Build a Singularity container

The Wave CLI supports building a [Singularity][singularity]. A target build repository, specified with the `--build-repo` argument, is required to build a Singularity container. You can build a Singularity container from several sources.

<details open>
<summary>**Build a Singularity container**</summary>

- A [SingularityCE][singularityce] def file
- A Docker container image with an optional local context directory
- Conda packages

The following limitations apply:

- The `linux/arm64` platform is not currently supported

**Related CLI arguments**

The following arguments are used to build a Singularity container:

- `--build-repo`: A target repository to save the built container to.
- `--freeze`: Enable a container freeze.
- `--singularity` and `-s`: Build a Singularity container.
- `--tower-token`: A Seqera Platform auth token so that Wave can access your private registry credentials. Not required if the `TOWER_ACCESS_TOKEN` environment variable is set.
- `--tower-workspace-id`: A Seqera Platform workspace ID, such as `1234567890`, where credentials may be stored.

**Example usage**

In the following example, a Docker base image is augmented:

```
wave -i alpine --layer context-dir/ --build-repo docker.io/user/repo
```

In the following example, a SingularityCE def file is specified:

```
wave -f hello-world.def --singularity --freeze --build-repo docker.io/user/repo
```

In the following example, two Conda packages are specified:

```
wave --conda-package bamtools=2.5.2 --conda-package samtools=1.17 --freeze --singularity --build-repo docker.io/user/repo
```

</details>

## Build a container and freeze to a container registry

The Wave CLI supports building a container and persisting the container to a container registry, such as DockerHub. You can refer to this frozen container image in a Dockerfile or [Nextflow][nextflow] pipeline in the same way as any other container.

<details open>
<summary>**Build a container and freeze to a container registry**</summary>

To freeze a container, you must ensure the following conditions are met:

- You created a Seqera access token.
- You specified the destination container registry credentials in Seqera Platform.
- You specify the Seqera access token via either the `TOWER_ACCESS_TOKEN` environment variable or the `--tower-token` Wave command line option.

**Related CLI arguments**

The following arguments are used to freeze a container build:

- `--build-repo`: A target repository to save the built container to.
- `--freeze`: Enable a container freeze.
- `--tower-token`: A Seqera Platform auth token so that Wave can access your private registry credentials. Not required if the `TOWER_ACCESS_TOKEN` environment variable is set.
- `--tower-workspace-id`: A Seqera Platform workspace ID, such as `1234567890`, where credentials may be stored.

**Example usage**

In the following example, the `alpine` container image is frozen to a private DockerHub image registry. The `--tower-token` argument is not required if the `TOWER_ACCESS_TOKEN` environment variable is defined.

```
wave -i alpine --freeze \
  --build-repo docker.io/user/repo --tower-token <TOWER_TOKEN>
```

</details>

## Mirror a container image to another registry

The Wave CLI supports mirroring, i.e., copying containers to a container registry of your choice.

<details open>
<summary>**Mirror a container image to another registry**</summary>

To mirror a container image, you must ensure the following conditions are met:

- You created a Seqera access token.
- You specified the destination container registry credentials in Seqera Platform.
- You specify the Seqera access token via either the `TOWER_ACCESS_TOKEN` environment variable or the `--tower-token` Wave command line option.

**Related CLI arguments**

The following arguments are used to freeze a container build:

- `--mirror`: Enable container mirror mode.
- `--build-repo`: A target repository to save the built container to.
- `--tower-token`: A Seqera Platform auth token so that Wave can access your private registry credentials. Not required if the `TOWER_ACCESS_TOKEN` environment variable is set.

**Example usage**

In the following example, the [`samtools:0.1.16--2`][samtools] container image is mirrored to a private DockerHub image registry. The `--tower-token` argument is not required if the `TOWER_ACCESS_TOKEN` environment variable is defined.

```
wave -i quay.io/biocontainers/samtools:0.1.16--2 --mirror \
  --build-repo docker.io/<USERNAME>/containers --tower-token <TOWER_TOKEN>
```

</details>

[conda]: https://anaconda.org/anaconda/repo
[conda-lock]: https://github.com/conda/conda-lock
[nextflow]: https://www.nextflow.io/
[samtools]: https://quay.io/repository/biocontainers/samtools?tab=tags
[singularity]: https://docs.sylabs.io/guides/latest/user-guide/introduction.html
[singularityce]: https://docs.sylabs.io/guides/latest/user-guide/definition_files.html
[start]: /wave_docs/wave_repo/docs/tutorials/wave-cli.mdx
