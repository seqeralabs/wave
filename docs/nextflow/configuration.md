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
: Enables the execution of Wave containers. Default: `false`.

`wave.endpoint`
: Wave service endpoint. Default: `https://wave.seqera.io`.

`wave.freeze`
: _Requires Nextflow version 23.07.0-edge or later._
: Enables freeze mode to permanently store provisioned Wave containers.
  The target registry must be specified by `wave.build.repository`.
  Specifying a custom cache repository using `wave.build.cacheRepository` is also recommended.
  Default: `false`.

`wave.mirror`
: _Requires Nextflow version 24.09.1-edge or later._
: Enables Wave container mirroring.
  The target registry must be specified by `wave.build.repository`.
  This option is only compatible with `wave.strategy = 'container'` and cannot be used with `wave.freeze`.
  Default: `false`.

`wave.strategy`
: Strategy used to resolve ambiguous Wave container requirements.
  Default: `'container,dockerfile,conda'`.

## Build

Use the following options to configure Wave build settings:

`wave.build.cacheRepository`
: Container repository URL for caching image layers built by the Wave service.
  Requires corresponding credentials to be configured in your Seqera account.

`wave.build.compression.force`
: _Requires Nextflow version 25.05.0-edge or later._
: Enables forceful compression for all layers, including existing layers
  Default: `false`.

`wave.build.compression.level`
: _Requires Nextflow version 25.05.0-edge or later._
: Compression level used when building containers. Valid ranges depend on compression type: `0`-`9` for `gzip` and `estargz`, and `0`-`22` for `zstd`. Higher values provide better compression but slower build times.

`wave.build.compression.mode`
: _Requires Nextflow version 25.05.0-edge or later._
: Compression algorithm for container builds. Options include: `gzip`, `estargz`, or `zstd`.
  Default: `gzip`.

`wave.build.conda.basePackages`
: Conda packages to always include in the container.
  Default: `conda-forge::procps-ng`.

`wave.build.conda.commands`
: Commands to add to the Dockerfile for Conda-based images.

`wave.build.conda.mambaImage`
: Mamba container image used for building Conda-based containers.
  Must be a [micromamba-docker](https://github.com/mamba-org/micromamba-docker) compatible image.

`wave.build.repository`
: Container repository URL where Wave uploads built images.
  Requires corresponding credentials to be configured in your Seqera account.

## Conda

Use the following options to configure Wave Conda settings:

`wave.conda.mambaImage`
: Mamba container image used for building Conda-based containers.
  Must be a [micromamba-docker](https://github.com/mamba-org/micromamba-docker) compatible image.

`wave.conda.commands`
: Additional Dockerfile commands to include when building Conda-based container images.
  Commands are inserted into the generated Dockerfile during the build process.

## HTTP client

Use the following options to configure Wave  HTTP client settings:

`wave.httpClient.connectTimeout`
: _Requires Nextflow version 22.06.0-edge or later._
: Connection timeout for the Wave HTTP client.
  Default: `30s`.

`wave.httpClient.maxRate`
: _Requires Nextflow version 22.06.0-edge or later._
: Maximum request rate for the Wave HTTP client
  Default: `1/sec`.

## Retry policy

Use the following options to configure Wave retry policy settings:

`wave.retryPolicy.delay`
: _Requires Nextflow version 22.06.0-edge or later._
: Delay when retrying failing HTTP requests.
  Default: `450ms`.

`wave.retryPolicy.jitter`
: _Requires Nextflow version 22.06.0-edge or later._
: Jitter factor for randomly varying retry delays.
  Default: `0.25`.

`wave.retryPolicy.maxAttempts`
: _Requires Nextflow version 22.06.0-edge or later._
: Maximum retry attempts for failing HTTP requests.
  Default: `5`.

`wave.retryPolicy.maxDelay`
: _Requires Nextflow version 22.06.0-edge or later._
: Maximum delay when retrying failing HTTP requests
  Default: `90s`.

## Scan

Use the following options to configure Wave scan settings:

`wave.scan.allowedLevels`
: _Requires Nextflow version 24.09.1-edge or later._
: Comma-separated list of allowed vulnerability levels for container scanning. Requires `wave.scan.mode = 'required'` to be set.
  Options: `low`, `medium`, `high`, and `critical`.

`wave.scan.mode`
: _Requires Nextflow version 24.09.1-edge or later._
: Wave container security scanning mode.
: Options:
: - `none`: No container security scanning.
: - `async`: Containers are scanned for security vulnerabilities. The task is executed regardless of the scan result.
: - `required`: Containers are scanned for security vulnerabilities. The task is executed only if the container is free of vulnerabilities.
