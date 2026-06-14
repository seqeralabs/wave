---
title: Configuration
description: Configure the Wave CLI.
date: 2026-05-01
tags: [wave cli, configuration, seqera platform]
---

Configure Wave CLI through CLI arguments and environment variables. The settings below cover external connections to Seqera Platform and the Wave service, and per-build options for the build context and layer caching.

:::note
When a setting accepts both forms, the CLI argument takes precedence over the environment variable.
:::

## Seqera Platform integration

Connect Wave CLI to a Seqera Platform instance to access private registry credentials and select a workspace.

`--tower-token`
: A Seqera Platform auth token so that Wave can access your private registry credentials.
  Can be set using the `TOWER_ACCESS_TOKEN` environment variable.

`--tower-endpoint`
: For Enterprise customers, the URL endpoint for your instance. For example, `https://api.cloud.seqera.io`.
  Can be set using the `TOWER_API_ENDPOINT` environment variable.

`--tower-workspace-id`
: A Seqera Platform workspace ID where credentials may be stored. For example, `1234567890`.
  Can be set using the `TOWER_WORKSPACE_ID` environment variable.

## Wave service endpoint

By default, Wave CLI sends requests to the hosted Wave service at `https://wave.seqera.io`. Override this to point the CLI at a self-hosted Wave or Wave Lite deployment.

`--wave-endpoint`
: The Wave service URL. For example, `https://wave.example.com`.
  Can be set using the `WAVE_ENDPOINT` environment variable.

## Build context

Provide a build context directory to allow `ADD` and `COPY` instructions in a Dockerfile to reference local files.

`--context`
: A directory containing the build context for a Dockerfile build.

:::important
The following file size limits apply to build context directories:

- A file must be no larger than 1 MB.
- A directory must be no larger than 10 MB, inclusive of all files.
:::

## Image layer caching

A successful build returns a unique `wave.seqera.io` URL on each invocation. Unchanged image layers retain their digest SHA and can be reused from image layer caches on subsequent builds.

`--cache-repo`
: A container registry where Wave caches new image layers it creates.

:::important
This caching behavior doesn't apply when building Singularity containers, where the image file name is considered authoritative. For example, the `wave.seqera.io-wt-53014394cbda-nextflow-rnaseq-nf-v1.1.img` file is the result of a Singularity container build.
:::

To avoid unnecessary image downloads, freeze the built container to get a stable URL.
