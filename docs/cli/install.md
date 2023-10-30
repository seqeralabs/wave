---
title: Install the Wave CLI
---

To install the `wave` CLI for your platform, complete the following steps:

1.  Download the [latest version of the Wave CLI][download] for your platform.
2.  In a new terminal, complete the following steps:

    1. Move the executable from your downloads folder to a location in your `PATH`, such as `~/bin`. For example: `mv wave-cli-0.8.0-macos-x86_64 ~/bin/wave`
    2. Ensure that the executable bit is set. For example: `chmod u+x ~/bin/wave`

3.  Verify that you can build containers with Wave:

    1.  Create a basic `Dockerfile`:

        ```
        cat << EOF > ./Dockerfile
        FROM busybox:latest
        EOF
        ```

    2.  Use the CLI to build the container:

        ```
        wave -f Dockerfile
        ```

        Example output:

        ```
        wave.seqera.io/wt/xxxxxxxxxxxx/wave/build:xxxxxxxxxxxxxxxx
        ```

[download]: https://github.com/seqeralabs/wave-cli/releases
