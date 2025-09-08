---
title: Configuration options
tags: [nextflow, configuration, wave]
---

This reference lists the Wave configuration variables that can be used to customize Wave behavior in your Nextflow pipelines. Configure these settings in your pipelines Nextflow configuration file.

:::note
Nextflow integration with Wave requires Nextflow 22.10.0, or later.
:::

## General

Use the following options to configure general Wave settings:

`wave.enabled`
: Enables the execution of Wave containers (default: `false`).

`wave.endpoint`
: Specifies the Wave service endpoint (default: `https://wave.seqera.io`).

`wave.freeze`
: Enables freeze mode to permanently store provisioned Wave containers (default: `false`).
  The target registry must be specified by `wave.build.repository`.
  It is also recommended to specify a custom cache repository using `wave.build.cacheRepository`.
  Requires Nextflow version 23.07.0-edge or later.

`wave.mirror`
: Enables Wave container mirroring (default: `false`).
  The target registry must be specified by `wave.build.repository`.
  This option is only compatible with `wave.strategy = 'container'` and cannot be used with `wave.freeze`.
  Requires Nextflow version 24.09.1-edge or later.

`wave.strategy`
: Sets the strategy used when resolving ambiguous Wave container requirements (default: `'container,dockerfile,conda'`).

## Build

Use the following options to configure build settings:

`wave.build.cacheRepository`
: Specifies the container repository URL for caching image layers built by the Wave service.
  Requires corresponding credentials to be configured in your Platform account.

`wave.build.compression.force`
: Enables forceful compression for all layers, including existing layers (default: `false`).
  Requires Nextflow version 25.05.0-edge or later.

`wave.build.compression.level`
: Sets the compression level used when building containers. Valid ranges depend on compression type: 0-9 for `gzip/estargz`, 0-22 for `zstd`. Higher values provide better compression but slower build times.
  Requires Nextflow version 25.05.0-edge or later.

`wave.build.compression.mode`
: Sets the compression algorithm for container builds. Options include: `gzip`, `estargz`, or `zstd` (default: `gzip`).
  Requires Nextflow version 25.05.0-edge or later.

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

Use the following options to configure Conda settings:

`wave.conda.mambaImage`
: Specifies the Mamba container image used for building Conda-based containers.
  Must be a [micromamba-docker](https://github.com/mamba-org/micromamba-docker) compatible image.

`wave.conda.commands`
: Specifies additional Dockerfile commands to include when building Conda-based container images.
  Commands are inserted into the generated Dockerfile during the build process.

## HTTP client

Use the following options to configure HTTP client settings:

`wave.httpClient.connectTimeout`
: Sets the connection timeout for the Wave HTTP client  (default: `30s`).
  Requires Nextflow version 22.06.0-edge or later.

`wave.httpClient.maxRate`
: Sets the maximum request rate for the Wave HTTP client (default: `1/sec`).
  Requires Nextflow version 22.06.0-edge or later.

## Retry policy

Use the following options to configure retry policy settings:

`wave.retryPolicy.delay`
: Sets the delay when retrying failing HTTP requests (default: `450ms`).
  Requires Nextflow version 22.06.0-edge or later.

`wave.retryPolicy.jitter`
: Sets the jitter factor for randomly varying retry delays (default: `0.25`).
  Requires Nextflow version 22.06.0-edge or later.

`wave.retryPolicy.maxAttempts`
: Sets the maximum retry attempts for failing HTTP requests (default: `5`).
  Requires Nextflow version 22.06.0-edge or later.

`wave.retryPolicy.maxDelay`
: Sets the maximum delay when retrying failing HTTP requests (default: `90s`).
  Requires Nextflow version 22.06.0-edge or later.

## Scan

Use the following options to configure scan settings:

`wave.scan.allowedLevels`
: Specifies a comma-separated list of allowed vulnerability levels for container scanning. Requires `wave.scan.mode = 'required'` to be set.
  Options include: `low`, `medium`, `high`, `critical`.
  This option is only compatible with `wave.scan.mode = 'required'`.
  Requires Nextflow version 24.09.1-edge or later.

`wave.scan.mode`
: Sets the Wave container security scanning mode.
  Requires Nextflow version 24.09.1-edge or later.
: Options include:
: - `none`: No container security scanning
: - `async`: Containers are scanned for security vulnerabilities. The task is executed regardless of the scan result.
: - `required`: Containers are scanned for security vulnerabilities. The task is executed only if the container is free of vulnerabilities.
