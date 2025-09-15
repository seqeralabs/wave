---
title: Configuration reference
description: Wave configuration variables for customizing behavior in Nextflow pipelines
tags: [nextflow, wave, use configuration]
---

This reference lists the Wave configuration variables that can be used to customize Wave behavior in your Nextflow pipelines. Configure these settings in your Nextflow configuration file.

:::tip
For a full list of Nextflow configuration options, see [Configuration options](https://www.nextflow.io/docs/latest/reference/config.html).
:::

:::note
Nextflow integration with Wave requires Nextflow 22.10.0 or later.
:::

## General

Use the following options to configure general Wave settings:

`wave.enabled`
: Enables the execution of Wave containers (default: `false`).

`wave.endpoint`
: Specifies the Wave service endpoint (default: `https://wave.seqera.io`).

`wave.freeze`
: _Requires Nextflow version 23.07.0-edge or later._
: Enables freeze mode to permanently store provisioned Wave containers (default: `false`).
  The target registry must be specified by `wave.build.repository`.
  Specifying a custom cache repository using `wave.build.cacheRepository` is also recommended.

`wave.mirror`
: _Requires Nextflow version 24.09.1-edge or later._
: Enables Wave container mirroring (default: `false`).
  The target registry must be specified by `wave.build.repository`.
  This option is only compatible with `wave.strategy = 'container'` and cannot be used with `wave.freeze`.

`wave.strategy`
: Sets the strategy used when resolving ambiguous Wave container requirements (default: `'container,dockerfile,conda'`).

## Build

Use the following options to configure Wave build settings:

`wave.build.cacheRepository`
: Specifies the container repository URL for caching image layers built by the Wave service.
  Requires corresponding credentials to be configured in your Platform account.

`wave.build.compression.force`
: _Requires Nextflow version 25.05.0-edge or later._
: Enables forceful compression for all layers, including existing layers (default: `false`).

`wave.build.compression.level`
: _Requires Nextflow version 25.05.0-edge or later._
: Sets the compression level used when building containers. Valid ranges depend on compression type: `0`-`9` for `gzip` and `estargz`, and `0`-`22` for `zstd`. Higher values provide better compression but slower build times.

`wave.build.compression.mode`
: _Requires Nextflow version 25.05.0-edge or later._
: Sets the compression algorithm for container builds. Options include: `gzip`, `estargz`, or `zstd` (default: `gzip`).

`wave.build.conda.basePackages`
: Sets Conda packages to always include in the container (default: `conda-forge::procps-ng`).

`wave.build.conda.commands`
: Sets commands to add to the Dockerfile for Conda-based images.

`wave.build.conda.mambaImage`
: Specifies the Mamba container image used for building Conda-based containers.
  Must be a [micromamba-docker](https://github.com/mamba-org/micromamba-docker) compatible image.

`wave.build.repository`
: Specifies the container repository URL where Wave uploads built images.
  Requires corresponding credentials to be configured in your Platform account.

## Conda

Use the following options to configure Wave Conda settings:

`wave.conda.mambaImage`
: Specifies the Mamba container image used for building Conda-based containers.
  Must be a [micromamba-docker](https://github.com/mamba-org/micromamba-docker) compatible image.

`wave.conda.commands`
: Specifies additional Dockerfile commands to include when building Conda-based container images.
  Commands are inserted into the generated Dockerfile during the build process.

## HTTP client

Use the following options to configure Wave  HTTP client settings:

`wave.httpClient.connectTimeout`
: _Requires Nextflow version 22.06.0-edge or later._
: Sets the connection timeout for the Wave HTTP client (default: `30s`).

`wave.httpClient.maxRate`
: _Requires Nextflow version 22.06.0-edge or later._
: Sets the maximum request rate for the Wave HTTP client (default: `1/sec`).

## Retry policy

Use the following options to configure Wave retry policy settings:

`wave.retryPolicy.delay`
: _Requires Nextflow version 22.06.0-edge or later._
: Sets the delay when retrying failing HTTP requests (default: `450ms`).

`wave.retryPolicy.jitter`
: _Requires Nextflow version 22.06.0-edge or later._
: Sets the jitter factor for randomly varying retry delays (default: `0.25`).

`wave.retryPolicy.maxAttempts`
: _Requires Nextflow version 22.06.0-edge or later._
: Sets the maximum retry attempts for failing HTTP requests (default: `5`).

`wave.retryPolicy.maxDelay`
: _Requires Nextflow version 22.06.0-edge or later._
: Sets the maximum delay when retrying failing HTTP requests (default: `90s`).

## Scan

Use the following options to configure Wave scan settings:

`wave.scan.allowedLevels`
: _Requires Nextflow version 24.09.1-edge or later._
: Specifies a comma-separated list of allowed vulnerability levels for container scanning. Requires `wave.scan.mode = 'required'` to be set.
  Options include: `low`, `medium`, `high`, and `critical`.

`wave.scan.mode`
: _Requires Nextflow version 24.09.1-edge or later._
: Sets the Wave container security scanning mode.
: Options include:
: - `none`: No container security scanning.
: - `async`: Containers are scanned for security vulnerabilities. The task is executed regardless of the scan result.
: - `required`: Containers are scanned for security vulnerabilities. The task is executed only if the container is free of vulnerabilities.
