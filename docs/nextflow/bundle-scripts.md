---
title: Bundling pipeline scripts
description: Learn how Wave packages scripts from Nextflow pipelines into container images
date created: 2025-11-19
date edited: 2025-11-19
tags: [nextflow, wave, scripts, guides]
---

Wave bundles certain executable scripts from your Nextflow pipeline into container images through container augmentation. This capability is essential on cloud platforms that lack shared file systems.

## Script locations in Nextflow

Nextflow pipelines can store scripts in multiple locations. Wave treats each location differently:

| Location                                            | Nextflow project path             | Wave behavior         |
|-----------------------------------------------------|-----------------------------------|-----------------------|
| [Workflow `bin` directory](#workflow-bin-directory) | `${projectDir}/bin/`              | Bundled conditionally |
| [Module `bin` directory](#module-bin-directory)     | `${moduleDir}/resources/usr/bin/` | Always bundled        |
| [Templates directory](#template-directory)          | `${projectDir}/templates/`        | Not bundled           |
| [Library directory](#library-directory)             | `${projectDir}/lib/`              | Not bundled           |

### Workflow `bin` directory

Wave doesn't bundle scripts from the workflow `bin` directory by default. Nextflow uploads these scripts to the work directory at runtime using cloud storage APIs.

When Fusion is enabled or when using the AWS Fargate executor, Wave bundles workflow `bin` scripts into a container layer.

### Module `bin` directory

Wave bundles module `bin` scripts into a container layer when you enable the module binaries feature.

```groovy
nextflow.preview.module.binaries = true

wave {
  enabled = true
}
```

Module scripts must be placed in `<MODULE_DIRECTORY>/resources/usr/bin/`. Wave bundles them into a container layer at `/usr/local/bin/` and adds them to `$PATH`.

:::info
Wave uses content-based fingerprinting for bundled scripts, ignoring file timestamps. This ensures that modifying only timestamps (without changing file contents) won't invalidate Nextflow's task cache or trigger unnecessary container rebuilds.
:::

:::warning
Module binaries do not work on cloud executors without Wave. Tasks will fail if Wave is not enabled.
:::

### Template directory

Wave does not bundle template files. When a process uses a template, Nextflow evaluates the template during task preparation (before submission) by substituting all variables with their actual values. The evaluated content is then embedded directly into the task's execution script (`.command.sh`). Wave only receives and containers only contain the final, fully-evaluated script—never the original template files with placeholders.

### Library directory

Wave does not bundle Groovy library files from `${projectDir}/lib/`. These files contain custom classes and utilities that extend Nextflow's workflow orchestration capabilities. They are compiled and loaded into Nextflow's JVM classpath at pipeline startup, where they become part of the workflow engine itself—not the containerized task execution environments. Since library files operate at the workflow coordination layer rather than the task execution layer, they remain outside Wave's containerization scope.

## Modifying scripts

Wave uses content-based fingerprinting to determine when containers need rebuilding. This approach ensures reproducible builds and preserves Nextflow's resume functionality.

Container fingerprints are included in Nextflow's task hash calculation. When fingerprints change, cached task results become invalid and tasks must re-run.

## Pull and inspect scripts

To pull and inspect containers:

1. Pull Wave-generated containers:

    ```bash
    docker pull wave.seqera.io/wt/<HASH>/<IMAGE>
    ```

1. List bundled scripts

    ```bash
    docker run --rm wave.seqera.io/wt/<HASH>/<IMAGE> ls -la /usr/bin/
    ```

1. Verify script availability

    ```bash
    docker run --rm wave.seqera.io/wt/<HASH>/<IMAGE> which <YOUR_SCRIPT>
    ```
