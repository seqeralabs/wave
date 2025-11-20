---
title: Bundling pipeline scripts
description: Learn how Wave packages scripts from Nextflow pipelines into container images
date created: 2025-11-19
date edited: 2025-11-19
tags: [nextflow, wave, scripts, guides]
---

On cloud platforms without shared file systems, containerized tasks cannot access scripts from a shared directory. Wave solves this by packaging certain scripts directly into the container image, making them available in the task execution environment.

This guide explains which script directories Wave bundles when bundling or augmenting containers.

## Script locations in Nextflow

Nextflow pipelines can store scripts in multiple locations. Wave treats each location differently:

| Location                                                   | Nextflow project path             | Wave behavior         |
|------------------------------------------------------------|-----------------------------------|-----------------------|
| [Project `bin` directory](#projectbin-directory)           | `${projectDir}/bin/`              | Bundled conditionally |
| [Module `bin` directory](#module-bin-directory)            | `${moduleDir}/resources/usr/bin/` | Always bundled        |
| [Module `templates` directory](#module-template-directory) | `${moduleDir}/templates/`         | Not bundled           |
| [Project `lib` directory](#project-lib-directory)          | `${projectDir}/lib/`              | Not bundled           |

### Project `bin` directory

Wave does not bundle scripts from the workflow `bin` directory by default. Nextflow uploads these scripts to the work directory at runtime using cloud storage APIs. This approach adds network overhead and can be inefficient when launching many tasks.

When you enable Fusion or when you use the AWS Fargate executor, Wave bundles workflow `bin` scripts into a container layer. This provides better performance but ties scripts to specific execution environments. For portable script bundling, use the [module `bin` directory](#module-bin-directory) instead.

See [The `bin` directory](https://nextflow.io/docs/latest/sharing.html#the-bin-directory) for more information.

### Module `bin` directory

Wave bundles scripts from module `bin` directories into container layers when you enable Wave and the module binaries feature.

```groovy
nextflow.enable.moduleBinaries = true

wave {
  enabled = true
}
```

Module scripts must be placed in `<MODULE_DIRECTORY>/resources/usr/bin/` folders. See [Module binaries](https://nextflow.io/docs/latest/module.html#module-binaries) for more information.

:::warning
Module binaries do not work on cloud executors without Wave.
:::

:::info
Wave uses content-based fingerprinting for bundled scripts, ignoring file timestamps. This ensures that modifying only timestamps (without changing file contents) won't invalidate Nextflow's task cache or trigger unnecessary container rebuilds.
:::

### Module `templates` directory

Wave does not bundle template files. When a process uses a template, Nextflow evaluates the template during task preparation (before submission) by substituting all variables with their actual values. The evaluated content is then embedded directly into the task's execution script (`.command.sh`). Wave receives only the final, fully-evaluated script, and containers contain only this evaluated version—never the original template files with placeholders.

See [Module templates](https://nextflow.io/docs/latest/module.html#module-templates) for more information.

### Project `lib` directory

Wave does not bundle Groovy library files from `${projectDir}/lib/`. These files contain custom classes and utilities that extend Nextflow's workflow orchestration capabilities. They are compiled and loaded into Nextflow's JVM classpath at pipeline startup, where they become part of the workflow engine itself, not the containerized task execution environments. Since library files operate at the workflow coordination layer rather than the task execution layer, they remain outside Wave's containerization scope.

See [The `lib` directory](https://nextflow.io/docs/latest/sharing.html#the-lib-directory) for more information.

## Modifying scripts

Wave uses content-based fingerprinting to determine when containers need rebuilding. This approach ensures reproducible builds and preserves Nextflow's resume functionality.

Container fingerprints are included in the Nextflow task hash calculation. When fingerprints change, cached task results become invalid and tasks must re-run.

## Pull and inspect container scripts

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

:::note
Update the container path if you are using a private container registry.
:::
