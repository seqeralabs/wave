---
title: Bundling pipeline scripts
description: Learn how Wave bundles scripts from Nextflow pipelines into container images
date created: 2025-11-19
date edited: 2025-11-19
tags: [nextflow, wave, scripts, guides]
---

On cloud platforms without shared file systems, containerized tasks cannot access scripts from a shared directory. Wave solves this by packaging certain scripts directly into the container image and making them available in the task execution environment.

This guide explains which `bin` script directories Wave bundles when building container images.

## Script location and behavior

Nextflow pipelines can store `bin` scripts in multiple locations. Wave treats each location differently:

| Location                                                   | Nextflow project path             | Wave behavior         |
|------------------------------------------------------------|-----------------------------------|-----------------------|
| [Project `bin` directory](#project-bin-directory)          | `${projectDir}/bin/`              | Bundled conditionally |
| [Module `bin` directory](#module-bin-directory)            | `${moduleDir}/resources/usr/bin/` | Always bundled        |

### Project `bin` directory

Wave does not bundle scripts from the project [`bin` directory](https://nextflow.io/docs/latest/sharing.html#the-bin-directory) by default. Nextflow uploads these scripts to the work directory at runtime using cloud APIs. This approach adds network overhead and can be inefficient when launching many tasks.

When you enable Fusion or use the AWS Fargate executor, Wave bundles scripts from the project `bin` directory into a container layer. This provides better performance but ties scripts to specific execution environments.

Modifying scripts in the project `bin` directory modify the container fingerprint for all Wave containers in the workflow. See [Wave container fingerprinting](#wave-container-fingerprinting) for more information.

### Module `bin` directory

Wave bundles scripts from [module `bin` directories](https://nextflow.io/docs/latest/module.html#module-binaries) into container layers. Module `bin` directories are a portable script solution that works across execution environments.

You must enable Wave and the module binaries feature to bundle module scripts:

```groovy
nextflow.enable.moduleBinaries = true

wave {
  enabled = true
}
```
Scripts are scoped to specific modules and only affect containers that use those modules. Modifying a script in a module `bin` directory only changes the container fingerprint for processes that use that module, leaving other containers unchanged. See [Wave container fingerprinting](#wave-container-fingerprinting) for more information.

:::warning
Module binaries do not work on cloud executors without Wave.
:::

## Wave container fingerprinting

Wave bundles `bin` scripts into container layers and generates fingerprints that become part of the task hash:

- Scripts in module `bin` directory are added as a container layer
- When Fusion or the AWS Fargate executor are enabled, scripts in project `bin` directory are added as a container layer
- Script layers receive a fingerprint based on content, ignoring file timestamps
- Script layer fingerprints are incorporated in the container fingerprint

If you modify `bin` scripts, Wave generates a new layer fingerprint, which creates a new container fingerprint and invalidates the cache.

:::note
If Fusion or the AWS Fargate executor are enabled, Wave will include the project-level `bin` directory as a layer in all containers. Any changes to scripts in the project-level `bin` directory will change the layer and force recalculation of all containers in the workflow.
:::

## Inspect container scripts

To pull and inspect Wave containers:

1. Pull Wave-generated containers:

    ```bash
    docker pull wave.seqera.io/wt/<HASH>/<IMAGE>
    ```

1. List bundled scripts:

    ```bash
    docker run --rm wave.seqera.io/wt/<HASH>/<IMAGE> ls -la /usr/bin/
    ```

1. Verify script availability:

    ```bash
    docker run --rm wave.seqera.io/wt/<HASH>/<IMAGE> which <YOUR_SCRIPT>
    ```
