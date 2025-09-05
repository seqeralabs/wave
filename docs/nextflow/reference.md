---
title: Reference
tags: [nextflow, reference, wave]
---

## General

Configure general Wave settings with the following options:

`wave.enabled`
: Enables the execution of Wave containers (default: `false`).

`wave.endpoint`
: Specifies the Wave service endpoint (default: `https://wave.seqera.io`).

`wave.freeze`
: Enables freeze mode to permanently store provisioned Wave containers in the repository specified by `wave.build.repository`.(default: `false`).

`wave.mirror`
: Enables Wave container mirroring (default: `false`).

`wave.strategy`
: Sets the strategy used when resolving ambiguous Wave container requirements (default: `'container,dockerfile,conda'`).

## Build

Configure build settings with the following options:

`wave.build.cacheRepository`
: Specifies the container repository URL for caching image layers built by the Wave service.
  Requires corresponding credentials to be configured in your Platform account.

`wave.build.compression.force`
: Enables forceful compression for all layers, including existing layers (default: `false`).

`wave.build.compression.level`
: Sets the compression level used when building containers. Valid ranges depend on compression type: 0-9 for `gzip/estargz`, 0-22 for `zstd`. Higher values provide better compression but slower build times.

`wave.build.compression.mode`
: Sets the compression algorithm for container builds. Valid options are `gzip`, `estargz`, or `zstd`. (default: `gzip`).

`wave.build.conda.basePackages`
: Sets Conda packages to always include in the container (default: `conda-forge::procps-ng`).

`wave.build.conda.commands`
: Sets commands to add to the Dockerfile for Conda-based images.

`wave.build.conda.mambaImage`
: Specifies additional the Mamba container image used for building Conda-based containers.
  Must be a [micromamba-docker](https://github.com/mamba-org/micromamba-docker) compatible image.

`wave.build.repository`
: Specifies the container repository URL where Wave uploads built images.
  Requires corresponding credentials to be configured in your Platform account.

## Conda

Configure Conda settings with the following options:

`wave.conda.mambaImage`
: Specifies the Mamba container image used for building Conda-based containers.
  Must be a [micromamba-docker](https://github.com/mamba-org/micromamba-docker) compatible image.

`wave.conda.commands`
: Specifies additional Dockerfile commands to include when building Conda-based container images.
  Commands are inserted into the generated Dockerfile during the build process.

## HTTP client

Configure HTTP client settings with the following options:

`wave.httpClient.connectTimeout`
: Sets the initial delay before retrying failed HTTP requests (default: `30s`).

`wave.httpClient.maxRate`
: Sets the maximum request rate for the Wave HTTP client (default: `1/sec`).

## Retry policy

Configure retry policy settings with the following options:

`wave.retryPolicy.delay`
: Sets the initial delay when retrying failing HTTP requests (default: `450ms`).

`wave.retryPolicy.jitter`
: Sets the jitter factor for randomly varying retry delays (default: `0.25`).

`wave.retryPolicy.maxAttempts`
: Sets the maximum retry attempts for failing HTTP requests (default: `5`).

`wave.retryPolicy.maxDelay`
: Sets the maximum delay when retrying failing HTTP requests (default: `90s`).

## Scan

Configure scan settings with the following options:

`wave.scan.allowedLevels`
: Specifies a comma-separated list of allowed vulnerability levels for container scanning. Requires `wave.scan.mode = 'required'` to be set.
: Options include: `low`, `medium`, `high`, `critical`.

`wave.scan.mode`
: Sets the Wave container security scanning mode.
: Options include:
: - `none`: No container security scanning
: - `async`: Containers are scanned for security vulnerabilities. The task is executed regardless of the scan result.
: - `required`: Containers are scanned for security vulnerabilities. The task is executed only if the container is free of vulnerabilities.
