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

| Location     | Nextflow project path             | Wave behavior         |
|--------------|-----------------------------------|-----------------------|
| Workflow bin | `${projectDir}/bin/`              | Bundled conditionally |
| Module bin   | `${moduleDir}/resources/usr/bin/` | Always bundled        |
| Templates    | `${projectDir}/templates/`        | Not bundled           |
| Library      | `${projectDir}/lib/`              | Not bundled           |

### Workflow bin

By default, Wave does not receive scripts from the workflow `bin` directory. Nextflow uploads these scripts to the work directory at runtime using cloud storage APIs.

When Fusion or AWS Fargate executor are enabled, Wave receives and bundles workflow `bin` scripts. Wave creates a container layer containing the scripts and adds them to the container.

### Module bin

Wave bundles module `bin` scripts when Nextflow's module binaries feature is enabled.

```groovy
nextflow.preview.module.binaries = true

wave {
  enabled = true
}
```

Scripts are placed at `/usr/local/bin/` in the container and automatically available in `$PATH`.

:::warning
Module binaries do not work on cloud executors without Wave. Tasks will fail if Wave is not enabled.
:::

### Templates

Wave does not receive or bundle template files. Nextflow evaluates templates during task submission and embeds them directly into process execution scripts. Wave only sees the final evaluated process script, not the template source files.

### Library

Wave does not receive or bundle Groovy library files from `${projectDir}/lib/`. These files are loaded into Nextflow's JVM at pipeline launch and are not part of task execution environments. Library files are outside Wave's scope.

## Modifying scripts

- **Modify workflow bin script (with Fusion)**: Changes fingerprint → Wave rebuilds container
- **Modify module bin script**: Changes fingerprint → Wave rebuilds container
- **Add/remove scripts**: Changes fingerprint → Wave rebuilds container
- **Modify base container image**: Changes fingerprint → Wave rebuilds container
- **Change only timestamps**: No fingerprint change → Wave reuses cached container

## Test augmented containers

Pull and inspect containers Wave created:

**Pull Wave-generated container**

```bash
docker pull wave.seqera.io/wt/<hash>/<image>
```

**List bundled scripts**

```bash
docker run --rm wave.seqera.io/wt/<hash>/<image> ls -la /usr/local/bin/
```

**Verify script availability**

```bash
docker run --rm wave.seqera.io/wt/<hash>/<image> which your-script.sh
```
