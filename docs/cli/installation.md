---
title: Installation
description: Install the Wave CLI from a self-install package or Homebrew.
date: 2026-05-01
tags: [wave cli, installation]
---

The Wave CLI is distributed as an easy to use [self-install](#self-install) package and via [Homebrew](#homebrew).

The following sections describe how to install the Wave CLI on your system.

## Self-install

To self-install the latest Wave CLI release from GitHub:

1.  Download the [latest version of the Wave CLI][download] for your platform.

1.  In a new terminal, complete the following steps:

    1. Move the executable from your downloads folder to a location in your `PATH`, such as `~/bin`:

        ```bash
        mv wave-cli-0.8.0-macos-x86_64 ~/bin/wave
        ```

    1. Ensure that the executable permission is set:

        ```bash
        chmod u+x ~/bin/wave
        ```

1.  Verify that you can build containers with Wave:

    1.  Create a basic `Dockerfile`:

        ```Dockerfile title="Dockerfile"
        FROM busybox:latest
        ```

    1.  Build the container with the Wave CLI:

        ```bash
        wave -f Dockerfile
        ```

        If the install was successful, you should see output similar to the following:

        ```console
        wave.seqera.io/wt/xxxxxxxxxxxx/wave/build:xxxxxxxxxxxxxxxx
        ```

## Homebrew

To install the latest Wave CLI release with [Homebrew]:

1. Install Wave CLI with the following command:

    ```bash
    brew install seqeralabs/tap/wave-cli
    ```

1.  Verify that you can build containers with Wave:

    1.  Create a basic `Dockerfile`:

        ```dockerfile title="Dockerfile"
        FROM busybox:latest
        ```

    1.  Build the container with the Wave CLI:

        ```bash
        wave -f Dockerfile
        ```

        If the install was successful, you should see output similar to the following:

        ```console
        wave.seqera.io/wt/xxxxxxxxxxxx/wave/build:xxxxxxxxxxxxxxxx
        ```

[download]: https://github.com/seqeralabs/wave-cli/releases
[Homebrew]: https://brew.sh/
