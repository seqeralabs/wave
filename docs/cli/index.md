---
title: Wave CLI
description: A command-line interface for Wave's container provisioning capabilities.
date: 2026-05-07
tags: [wave cli, overview]
---

Wave CLI is a command-line wrapper around the Wave API. Use it to build, augment, freeze, mirror, and scan container images from your terminal. It works with hosted Wave and self-hosted deployments. Run it interactively or in CI pipelines to automate container workflows.

## Getting started

To get started with Wave CLI:

1. Install Wave CLI. See [Installation](./installation.md) for details.

2. Create a `Dockerfile` in the current directory:

    ```dockerfile title="Dockerfile"
    FROM busybox:latest
    ```

3. Build the container:

    ```bash
    wave -f Dockerfile
    ```

    A successful build returns a `wave.seqera.io` URL you can pull or run.

## Next steps

- [Configuration](./configuration.md): Connect to Seqera Platform, set the Wave endpoint, and tune build context and layer caching.
- [Use cases](./use-cases.md): See end-to-end examples for Dockerfile, Singularity, and Conda builds, plus augment, freeze, mirror, and scan.
