# Layered Pixi Builds (`conda/pixi:v1-fast`)

## Summary

This document describes the `conda/pixi:v1-fast` build template, the Pixi counterpart of `conda/micromamba:v2-fast` ([Layered Conda Builds ADR](20260930-conda-layered-builds.md)). It installs the environment exactly like `conda/pixi:v1`, then the same `condasplit` tool, run in the same build step, moves the installed files into at most 32 layer directories grouped by package, and the same BuildKit frontend adds each of them as its own layer. The template is opt-in: existing templates, the default template and existing container ids are unchanged.

## Context

`conda/pixi:v1` ships the whole Pixi environment as one layer:

```dockerfile
COPY --from=build /opt/wave/.pixi/envs/default /opt/wave/.pixi/envs/default
```

It has the same size limit, pull speed and build export problems that motivated `conda/micromamba:v2-fast`. The layering design doesn't depend on micromamba:

- `condasplit` takes any absolute prefix with `--src` and groups files by the `conda-meta/<dist>.json` records, which Pixi environments also have
- The frontend only looks for the `build` stage and the `COPY --link --from=build /layers/NN/ /` line

## Decision

Reuse the `conda/micromamba:v2-fast` design unchanged and add one template, with no change to the `condasplit` tool, the frontend or the tool image. All the technical decisions of the [Layered Conda Builds ADR](20260930-conda-layered-builds.md) apply, with the differences below.

### 1. New Versioned Template

**Decision:** Added `conda/pixi:v1-fast` (`BuildTemplate.CONDA_PIXI_V1_FAST`) instead of changing `conda/pixi:v1`.

**Rationale:**
- The container id hashes the generated container file, so changing `conda/pixi:v1` would change every existing pixi container id
- Named after the template it layers, as `conda/micromamba:v2-fast` is named after `conda/micromamba:v2`

### 2. Tool Invocation

**Decision:** The install `RUN` of `conda/pixi:v1` mounts the tool image and ends with:

```dockerfile
    && /opt/wave-tools/condasplit --src /opt/wave/.pixi/envs/default --out /layers \
        --slots 32 --max-layer-size 500000000 --own-layer-size 50000000
```

The final stage replaces the environment `COPY` with the single `COPY --link --from=build /layers/NN/ /` line, and keeps the `/shell-hook.sh` copy, `ENTRYPOINT` and `PATH` of `conda/pixi:v1`.

**Rationale:**
- Same slot count and size limits as `conda/micromamba:v2-fast`
- No `--exclude`: Pixi keeps its package cache in `~/.cache/rattler`, outside the environment, so it's never shipped, as with `conda/pixi:v1`. Files hardlinked between the cache and the environment are moved without their cache link, as for the micromamba `pkgs/` links
- Each layer recreates `/opt/wave/.pixi/envs` as 0755 root:root directories, which is what `WORKDIR` and `COPY` produce in `conda/pixi:v1`

### 3. Docker Only, No Lock Files

**Decision:** A `conda/pixi:v1-fast` request with `format: "sif"` fails with HTTP 400 `Build template 'conda/pixi:v1-fast' does not support Singularity format`. Conda lock file URLs are rejected as with `conda/pixi:v1`.

**Rationale:**
- A SIF image is a single squashfs file, so layering doesn't apply
- The template accepts exactly the inputs of `conda/pixi:v1`

## API Changes

| Value | Constant | Description |
|-------|----------|-------------|
| `conda/pixi:v1-fast` | `BuildTemplate.CONDA_PIXI_V1_FAST` | Layered multi-stage Pixi build, Docker only |

| Condition | Response |
|-----------|----------|
| `format: "sif"` | HTTP 400 `Build template 'conda/pixi:v1-fast' does not support Singularity format` |
| Non-`CONDA` package type | HTTP 400 `Package type '<type>' not supported by 'conda/pixi:v1-fast' build template` |
| Conda lock file URL | HTTP 400 `Conda lock file is not supported by 'conda/pixi:v1-fast' template` |

No new request fields and no new configuration: the template uses the `wave.build.condasplit-image` tool image of `conda/micromamba:v2-fast`.

## Files Changed

| Category | Files |
|----------|-------|
| API Models | `BuildTemplate.java` |
| Templates | `conda-pixi-v1-fast/dockerfile-conda-file.txt` |
| Helpers | `TemplateUtils.java`, `PixiHelper.groovy`, `ContainerHelper.groovy` |
| Tests | `*Test.groovy` for all modified components |
| Docs | `api.md`, `features/container-builds.mdx`, `cli/use-cases.md`, `install/reference.md`, `condasplit/README.md` |

## Consequences

### Positive
- Pixi environments get the same layer size limit, parallel pulls and faster layer export as `conda/micromamba:v2-fast`
- No new tool version, image or configuration

### Negative
- A `condasplit` upgrade now changes the container ids of both `-fast` templates
- The `conda/micromamba:v2-fast` limitations apply: a single file larger than 500 MB still produces a blob over 512 MB, and the install step is exported to the build cache as one blob

### Neutral
- `conda/pixi:v1` remains unchanged, and `conda/micromamba:v2` remains the default template
- The comments of the tool image sources still name only `conda/micromamba:v2-fast`: editing them changes the tool's source checksum, which requires a new tool version

## Follow-ups

- **Nextflow**: Add `conda/pixi:v1-fast` to the `wave.build.template` description in `plugins/nf-wave/src/main/io/seqera/wave/plugin/config/WaveConfig.groovy`
- **Wave CLI**: Add `conda/pixi:v1-fast` to the `--build-template` help text in `app/src/main/java/io/seqera/wave/cli/App.java`

## References

- [Layered Conda Builds ADR](20260930-conda-layered-builds.md)
- [Layered Conda builds API documentation](../docs/api.md#layered-conda-builds)

---

**Status:** Implemented
**Date:** 2026-10-03
**Authors:** Wave Team
