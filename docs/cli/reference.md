---
title: CLI reference
---

## Install the Wave CLI

To install the `wave` CLI for your platform, complete the following steps:

1.  Download latest version of the Wave CLI for your platform:

    - To install the latest release from GitHub:

      1.  Download the [latest version of the Wave CLI][download] for your platform.

      1.  In a new terminal, complete the following steps:

          1. Move the executable from your downloads folder to a location in your `PATH`, such as `~/bin`. For example: `mv wave-cli-0.8.0-macos-x86_64 ~/bin/wave`
          1. Ensure that the executable bit is set. For example: `chmod u+x ~/bin/wave`

    - To install the latest version with [Homebrew]:

        ```bash
        brew install seqeralabs/tap/wave-cli
        ```

1.  Verify that you can build containers with Wave:

    1.  Create a basic `Dockerfile`:

        ```
        cat << EOF > ./Dockerfile
        FROM busybox:latest
        EOF
        ```

    1.  Use the CLI to build the container:

        ```
        wave -f Dockerfile
        ```

        Example output:

        ```
        wave.seqera.io/wt/xxxxxxxxxxxx/wave/build:xxxxxxxxxxxxxxxx
        ```

[download]: https://github.com/seqeralabs/wave-cli/releases
[Homebrew]: https://brew.sh/

## Build a container

With the Wave CLI you can build Docker and Singularity containers from a variety of sources, including a Dockerfile, Singularity def file, file system directory, and Conda packages.

The following sections describe several common usage cases. To get started by creating an example Nextflow pipeline that uses Wave CLI, see [Get started][start].

[start]: ../get-started.md#wave-cli

### Augment a container with a directory

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

### Build a container from Conda packages

The Wave CLI supports building a container from a list of [Conda] packages.

<details open>
<summary>**Build a container from Conda packages**</summary>

**Related CLI arguments**

Conda builds support the following arguments:

- `--conda-base-image`: A base image for installing Conda packages. The default value is `mambaorg/micromamba:1.5.10-noble`.
- `--conda-channels`: One or more comma-separated channels. The default value is ` seqera,bioconda,conda-forge,defaults`.
- `--conda-file`: A [Conda lock file][conda-lock]. Can be a local file or a URL to a remote file.
- `--conda-package`: A Conda package to install. Can be specified multiple times. Expressions are supported, such as `bioconda::samtools=1.17` or `samtools>=1.0,<1.17`.
- ` --conda-run-command`: A Docker `RUN` command used when the container is built. Can be specified multiple times.

**Example usage**

In the following example, a container with the `samtools` and `bamtools` packages is built:

```
wave \
  --conda-package bamtools=2.5.2 \
  --conda-package samtools=1.17
```

[Conda]: https://anaconda.org/anaconda/repo
[conda-lock]: https://github.com/conda/conda-lock
</details>

### Build a container from a Dockerfile

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

### Build a Singularity container

The Wave CLI supports building a [Singularity]. A target build repository, specified with the `--build-repo` argument, is required to build a Singularity container. You can build a Singularity container from several sources.

<details open>
<summary>**Build a Singularity container**</summary>

- A [SingularityCE] def file
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

[Singularity]: https://docs.sylabs.io/guides/latest/user-guide/introduction.html
[SingularityCE]: https://docs.sylabs.io/guides/latest/user-guide/definition_files.html
</details>

### Build a container and freeze to a container registry

The Wave CLI supports building a container and persisting the container to a container registry, such as DockerHub. You can refer to this frozen container image in a Dockerfile or [Nextflow] pipeline in the same way as any other container.

<details open>
<summary>**Build a container and freeze to a container registry**</summary>

To freeze a container, you must ensure the following conditions are met:

- You created a Seqera Platform access token.
- You specified the destination container registry credentials in Seqera Platform.
- You specify the Seqera Platform access token via either the `TOWER_ACCESS_TOKEN` environment variable or the `--tower-token` Wave command line option.

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
  --build-repo docker.io/user/repo --tower-token <TOKEN>
```

[Nextflow]: https://www.nextflow.io/
</details>
