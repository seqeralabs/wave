---
title: Wave CLI
---

The Wave CLI is a convenient wrapper around the Wave API.

You can compose Wave CLI with other commands. The CLI returns the URL of the container build on stdout in the following format: `wave.seqera.io/wt/xxxxxxxxxxxx/wave/build:xxxxxxxxxxxxxxxx`

The first sequence is a 12-character unique access token.
The second sequence is a 16-character checksum that uniquely identifies the build.

By default, Wave container builds are ephemeral. You can persist, or _freeze_, a container build by saving it in a private container registry. For Wave to freeze your container image, you must provide a Seqera access token so that Wave can access your private registry credentials.

## Usage limits

The following usage limits apply:

- Anonymous usage
  - 25 container builds per day
  - 250 container pulls per hour
- Seqera Platform authenticated users
  - 100 container images per hour
  - 1,000 container images per minute

To authenticate with Seqera, define an access token in the `TOWER_ACCESS_TOKEN` environment variable or specify the token with the `--tower-token` CLI argument.

## Context directory size limits

The following file size limits apply when specifying a [context] directory:

- A file must be no larger than 1 MB each.
- A directory must be no larger than 10 MB, inclusive of all files.

## Image layer caching

When you build a container with the CLI, a successful build returns a unique `wave.seqera.io` URL each time. Unchanged image layers retain their digest SHA and therefore can be reused from image layer caches.

If you specify the `--cache-repo` argument, Wave caches new image layers that it creates to the container registry that you specify.

:::important
This caching behavior doesn't apply when building Singularity containers, where the image file name is considered authoritative. For example, the `wave.seqera.io-wt-53014394cbda-nextflow-rnaseq-nf-v1.1.img` file is the result of a Singularity container build.
:::

To avoid unnecessary image downloads, you can freeze the built container to provide a stable URL.

## Image security scans

As part of the build process, for containers that build successfully, Wave conducts a vulnerability scan using the [Trivy](https://trivy.dev/) security scanner. You must specify a Seqera access token to receive an email that links to the result of the security scan.

:::note
Singularity containers are not currently scanned.
:::

[context]: https://docs.docker.com/build/building/context/
