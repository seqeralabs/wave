---
title: Wave CLI
description: A command-line interface for Wave's container provisioning capabilities.
date: 2026-05-07
tags: [wave cli, overview]
---

Wave CLI is a command-line wrapper around the Wave API. Use it to build, augment, freeze, mirror, and scan container images from your terminal.

## Getting started

To build a container with Wave CLI:

1. Install Wave CLI with [Homebrew](https://brew.sh/):

    ```bash
    brew install seqeralabs/tap/wave-cli
    ```

    :::tip
    Wave CLI is also available as a self-install package. See [Installation](./installation.md) for details.
    :::

2. Create a basic `Dockerfile` in the current directory:

    ```dockerfile title="Dockerfile"
    FROM busybox:latest
    ```

3. Build the container with Wave:

    ```bash
    wave -f Dockerfile
    ```

    A successful build returns a `wave.seqera.io` URL you can pull or run:

    ```console
    wave.seqera.io/wt/xxxxxxxxxxxx/wave/build:xxxxxxxxxxxxxxxx
    ```

## Next steps

- [Configuration](./configuration.md): Connect to Seqera Platform, set the Wave endpoint, and tune build context and layer caching.
- [Use cases](./use-cases.md): See end-to-end examples for Dockerfile, Singularity, and Conda builds, plus augment, freeze, mirror, and scan.
