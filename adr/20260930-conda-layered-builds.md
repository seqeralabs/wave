# Layered Conda Builds (`conda/micromamba:v3`)

## Summary

This document describes the `conda/micromamba:v3` build template, which ships the Conda environment of a Docker image as multiple layers grouped by package, instead of one large layer. The template installs the environment exactly like `conda/micromamba:v2`. In the same build step, a small static Go tool (`condasplit`) moves the installed files into 32 fixed layer slots, each holding at most 500 MB before compression. The template is opt-in: existing templates, the default template and existing container ids are unchanged.

## Context

Every Conda template installs the whole environment with one `RUN` and ships it as one layer. The `conda/micromamba:v2` final stage ends with:

```dockerfile
COPY --from=build "$MAMBA_ROOT_PREFIX" "$MAMBA_ROOT_PREFIX"
```

For the reference environment (`bioconda::gatk4=4.6.2.0` and `bioconda::gcnvkernel=0.9`, 322 packages, linux/amd64) that single layer is 1977 MB compressed. This causes three problems:

1. **Size limits**: Some registries, proxies and CDN caches reject or refuse to cache blobs larger than 512 MB
2. **Pull speed**: Container runtimes download several layers at a time, but a single 2 GB layer is fetched over one connection and unpacked by one thread
3. **Build export time**: BuildKit compresses layers in parallel, one per core. The single layer takes 101 s to compress, against 16 s for the layered version

## Feature Overview

### What it does
- **Layered Environment**: Each package of at least 50 MB gets its own layer, smaller packages are grouped, and every layer holds at most 500 MB before compression
- **Same Content**: The image contains the same files as `conda/micromamba:v2`, with the same type, size, mode, owner, symlink target and hardlink grouping, minus the unused package cache (`/opt/conda/pkgs`)
- **Layer Plan**: The build log shows which packages went into which layer and how large each layer is
- **Docker Only**: Singularity requests are rejected with HTTP 400
- **Opt-in**: Nothing changes for requests that don't select the new template

### How it works

1. User submits a container request with `buildTemplate: "conda/micromamba:v3"`
2. Wave renders the v3 Dockerfile, naming the tool image configured with `wave.build.condasplit-image`, both as its BuildKit frontend (`# syntax=`, first line) and as the image mounted into the install step
3. Two-stage build executes:
   - **Stage 1 (build)**: Runs the `conda/micromamba:v2` install commands and prints the conda lock. A lock-file URL is first added to the stage with `ADD` and installed from the local copy, because micromamba can't read an explicit lock file from a URL. In the same `RUN`, it runs `condasplit` from the mounted tool image, which moves every file of `/opt/conda` (except `pkgs/`) into at most 32 directories `/layers/00`, `/layers/01`, …, each rooted at `/`, and prints the layer plan
   - **Stage 2 (prod)**: Starts from `{{base_image}}` and adds each layer directory as its own layer with `COPY --link`
4. The frontend builds stage 1 first, then builds the image with the template's single `COPY` line repeated for each layer directory

## Decision Drivers

1. **Layer Size**: Every Conda layer must stay under 512 MB compressed
2. **Container Id Stability**: Existing templates, the default template and existing container ids must not change
3. **Identical Content**: Layering must be invisible to the workload
4. **Minimal Surface**: No new services, persistence or request fields beyond one template value and one config property
5. **Build Cost**: Build disk usage and build cache size must not exceed `conda/micromamba:v2`
6. **Portability**: The layering step must run in any `mambaImage`, whatever its Linux distribution

## Technical Decisions

### 1. New Versioned Template

**Decision:** Added `conda/micromamba:v3` (`BuildTemplate.CONDA_MICROMAMBA_V3`) instead of changing `conda/micromamba:v2`. `BuildTemplate.defaultTemplate()` still returns `conda/micromamba:v2`.

| Value | Conda layers | Default |
|-------|--------------|---------|
| `conda/micromamba:v1` | 1 (single-stage) | No |
| `conda/micromamba:v2` | 1 | Yes (`CONDA` packages) |
| `conda/micromamba:v3` | Up to 32, one per used slot | No |
| `conda/pixi:v1` | 1 | No |

**Rationale:**
- The container id hashes the generated container file, so changing `conda/micromamba:v2` would change every existing id, trigger rebuilds and create new tags in the community registry
- The tool parameters are literal in the template, so tuning them later means a new template version, which is the intended behaviour
- Follows the versioned template approach introduced by the multi-stage build templates

### 2. At Most 32 Layers, One `COPY` Line

**Decision:** The tool creates at most 32 layer directories. The template has a single `COPY` line, which the frontend of decision 10 repeats for each of them.

```dockerfile
# the condasplit frontend repeats this line for each layer directory: /layers/00, /layers/01, ...
COPY --link --from=build /layers/NN/ /
```

**Rationale:**
- Wave generates the Dockerfile before the environment is solved, so the number of layers is not known in advance, and a Dockerfile can't loop
- Keeps the whole build in a single Dockerfile, with no extra solve job
- 32 layers allow about 16 GB of environment before compression. With a one-layer base image such as `ubuntu:24.04` the image stays around 35 layers, well under the ~125-layer overlay limit
- When an environment needs more than 32 layers, the two smallest layers are merged repeatedly until 32 remain, and the plan shows `WARN slots exceeded: merged <n> layers`

### 3. Go Static Tool in a `FROM scratch` Image

**Decision:** The layering logic is a Go program in the new `condasplit/` folder, built with the standard library only as a static binary (`CGO_ENABLED=0`). It ships as a multi-arch (amd64, arm64) `FROM scratch` image containing `/condasplit` and the frontend of decision 10, and is mounted read-only into the install step:

```dockerfile
RUN --mount=type=bind,from={{layers_image}},source=/,target=/opt/wave-tools \
    ... v2 install commands and conda lock markers ... \
    && /opt/wave-tools/condasplit --src /opt/conda --out /layers \
        --slots 32 --max-layer-size 500000000 --own-layer-size 50000000 --exclude pkgs
```

| Registry | Usage |
|----------|-------|
| `public.cr.stage-seqera.io/wave/condasplit` | Initial iteration and tests |
| `public.cr.seqera.io/wave/condasplit` | Production, published by the `build-condasplit.yml` workflow |

**Rationale:**
- A static binary with no runtime dependencies runs in any `{{mamba_image}}`. The default `mambaorg/micromamba:2-amazon2023` lacks `find` and `tar`, which rules out a shell implementation
- The binary is never written into any stage's filesystem, so it is absent from both the final image and the build cache
- BuildKit resolves the variant for the build's platform, pulls and caches it the same way as `{{mamba_image}}`. Each platform is built on nodes of its own architecture, so the tool always runs natively
- Images referenced by `RUN --mount ... from=` are added to the build credentials the same way `FROM` images are (`ContainerInspectServiceImpl`), so a tool image mirrored to a private registry is pulled with the configured registry credentials
- Follows the existing Wave tool image path (`buildkit`, `singularity`, `scanner`, `s5cmd`)

**Alternatives rejected:**

| Option | Reason |
|--------|--------|
| Python script in a `python:slim` image | Validated by the proof of concept, but brings a Python runtime and its CVEs into a build-time image |
| Bash script inline in the template | Needs GNU `find`, `tar` and `awk`, and parses the JSON receipts with awk |
| Java with a GraalVM native image | Heavy toolchain for a small tool |
| Go binary bundled in the Wave jar | Adds about 10 MB to the jar, needs per-platform selection in Wave, and the version must be printed into the Dockerfile |

### 4. Move Instead of Copy

**Decision:** The install and the tool run in the same `RUN`, and the tool uses `rename()` to move files from `/opt/conda` into the slot directories.

**Rationale:**
- Every installed file is in the same writable layer, so `rename()` only updates directory entries and the step takes seconds
- Build disk usage and build cache size stay at the `conda/micromamba:v2` level. The copy-based proof of concept doubled the environment on disk and took 21–28 s
- Hardlinks, owners, modes and timestamps survive automatically, because a moved file is the same file
- Completeness is verifiable: after the move, `/opt/conda` must contain only directories and the excluded `pkgs/`, otherwise the tool fails listing up to 20 offending paths
- Files inherited from lower image layers (custom `mambaImage`) are copied up by overlayfs on `rename()`, which keeps their metadata but splits hardlinks between them
- Each layer is rooted at `/`, so every slot left empty is the same empty directory, which the frontend drops. Used layers carry `/opt` and `/opt/conda`, both 0755 root:root as in the v2 image, and only the prefix's timestamps are kept

### 5. Grouping Rules

**Decision:** Files are grouped by owning Conda package, using the rules validated by the proof of concept.

| Rule | Behaviour |
|------|-----------|
| Atom | Regular files sharing `(dev, inode)` form one atom, counted once. Symlinks and empty directories are atoms of size 0. An atom is never split across layers |
| Ownership | `conda-meta/<dist>.json` files parsed in file-name order. The first claim wins and later claims are counted as clobbers. Unowned atoms go to `rest` |
| Own layer | Packages of at least 50 MB (`--own-layer-size`) each get a layer |
| Cap | At most 500 MB per layer before compression (`--max-layer-size`). A larger package is cut into chunks in path order |
| Shared layers | Remaining packages are packed first-fit-decreasing, by size and then name |
| Leftovers | `rest` is chunked into trailing layers of at most the cap. Originally empty directories are recreated here |
| Excluded | `pkgs` (`--exclude`) is left in place and not shipped |

**Rationale:**
- The 500 MB cap leaves 12 MB of headroom under 512 MB for tar headers and padding (about 1 KB per file). The largest compressed layer in the proof of concept was 399 MB
- The 50 MB threshold isolates large packages such as `gatk4`, `openjdk` and `pytorch`, while small packages don't each cost a layer
- Keeping hardlink groups in one atom preserves hardlink grouping: the same sets of paths share an inode as in `conda/micromamba:v2`. Micromamba hardlinks `pkgs/` to the environment, and splitting that pair produced a 4.4 GB leftover layer in the first proof of concept attempt. The `pkgs/` links stay in the build stage, so link counts in the image drop by the number of links that were in `pkgs/`
- Everything is sorted and nothing is random, so the same prefix content always gives the same plan
- Units are decimal (1 MB = 1,000,000 bytes)

### 6. Oversized File Policy

**Decision:** A single file (or hardlink group) larger than the cap gets a layer to itself, the layer plan shows `WARN oversized file`, and the build continues. Layers merged because of slot overflow are handled the same way.

**Rationale:**
- A file cannot be split across layers
- Failing the build would block environments, such as some GPU libraries, that build fine today as one layer
- The rest of the image still benefits from layering, and the warning makes the remaining large blob easy to diagnose
- The tool exits with `0` when only warnings were printed. Errors (missing or relative `--src`, missing `conda-meta/`, JSON parse error, I/O error, failed verify step) exit non-zero and fail the build like any other `RUN` failure

### 7. Package Cache Dropped

**Decision:** `conda/micromamba:v3` excludes `/opt/conda/pkgs` from the image. The other templates keep it.

**Rationale:**
- Extracted packages are not needed at runtime
- Reduces the image size (2013 MB to 1859 MB compressed for the reference environment)
- Dropping it from the existing templates would change every existing container id

### 8. Docker Only

**Decision:** A `conda/micromamba:v3` request with `format: "sif"` fails with HTTP 400 `Build template 'conda/micromamba:v3' does not support Singularity format`. There are no Singularity template files.

**Rationale:**
- A SIF image is a single squashfs file, so layering doesn't apply

### 9. Layer Plan in the Build Log

**Decision:** The tool prints the layer plan to the build log between `>> CONDA_LAYERS_START` and `<< CONDA_LAYERS_END`, after the unchanged conda lock markers. The plan is plain text, with one row per used slot listing size, file count and package names.

**Rationale:**
- Explains the result and helps diagnose oversized layers
- Conda lock extraction from the build log works exactly as for `conda/micromamba:v2`
- No persistence or UI change is needed

### 10. BuildKit Frontend Repeating the Layer `COPY` Line

**Decision:** The v3 Dockerfile starts with `# syntax={{layers_image}}`, which makes the tool image its BuildKit frontend. The frontend (`condasplit/frontend/`, entrypoint `/condasplit-frontend`) builds the `build` stage, lists `/layers`, repeats the `COPY --link --from=build /layers/NN/ /` line for each directory and builds the whole Dockerfile. Both builds are delegated to the built-in Dockerfile frontend.

**Rationale:**
- The image has exactly one layer per layer directory. BuildKit adds a layer for every `COPY`, even an empty one, so 32 fixed `COPY` lines gave a small environment such as `bioconda::samtools` 30 empty layers
- Built without the frontend, the Dockerfile fails on the missing `/layers/NN` instead of producing a wrong image
- The `buildctl` invocation doesn't change, since the built-in frontend forwards a build to the image named by the directive. Verified with Wave's arguments and the settings of the Kubernetes build pods (rootless, not privileged, `--oci-worker-no-process-sandbox`)
- The install stage runs once: the second build finds it in the cache of the first
- The Dockerfile syntax and the image content are those of the built-in frontend: the frontend only edits the Dockerfile text
- Shipping it in the tool image keeps one image to publish, configure, credential and mirror

**Alternatives rejected:**

| Option | Reason |
|--------|--------|
| 32 fixed `COPY` lines, empty ones kept | Harmless in size, but every image carries up to 31 empty layers |
| 32 fixed `COPY` lines, empty ones dropped by the frontend | Same images, but the template and the stored Dockerfile carry 32 lines |
| Frontend generating the LLB itself | Could create any number of layers, but reimplements the Dockerfile frontend |
| Remove the empty layers after the push | Replaces the image pushed by BuildKit with a new digest and needs registry writes from Wave, before the multi-platform index is assembled |
| Solve first, then write an exact Dockerfile | Needs a solve job before the build |

## API Changes

### New Template Value

| Value | Constant | Description |
|-------|----------|-------------|
| `conda/micromamba:v3` | `BuildTemplate.CONDA_MICROMAMBA_V3` | Layered multi-stage Micromamba build, Docker only |

No new request fields. The template accepts the same inputs as `conda/micromamba:v2`: conda file, conda lock file URL, custom `baseImage`/`containerImage`, `mambaImage`, `basePackages` and `commands`.

### New Error Responses

| Condition | Response |
|-----------|----------|
| `format: "sif"` | HTTP 400 `Build template 'conda/micromamba:v3' does not support Singularity format` |
| Non-`CONDA` package type | HTTP 400 `Package type '<type>' not supported by 'conda/micromamba:v3' build template` |

## Configuration Changes

| Property | Default | Description |
|----------|---------|-------------|
| `wave.build.condasplit-image` | `public.cr.stage-seqera.io/wave/condasplit:v1` | Tool image mounted into `conda/micromamba:v3` builds. Switches to `public.cr.seqera.io/wave/condasplit:v1` at the production release |

Each tool release gets a new immutable tag (`v1`, `v2`, …) and the config value may also pin a digest. The image reference is part of the v3 container file, so a tool upgrade changes container ids for v3 images only. Enterprise installs without access to `public.cr.seqera.io` mirror the image and set this property.

## Template Comparison

| Aspect | `conda/micromamba:v2` | `conda/micromamba:v3` |
|--------|-----------------------|-----------------------|
| Build stages | 2 | 2 |
| Conda environment layers | 1 | One per used slot, up to 32, at most 500 MB each before compression |
| Package cache (`/opt/conda/pkgs`) | Included | Dropped |
| Layer plan in build log | No | Yes |
| Build-time tool image | None | `condasplit`, frontend and mounted tool (not shipped) |
| Singularity support | Yes (single-stage) | No (HTTP 400) |
| Default template | Yes | No |

## Proof of Concept Results

Reference environment built with BuildKit v0.25.2, gzip compression and OCI media types. The proof of concept used a copy-based Python splitter in a separate stage; the grouping rules are the same.

| | Single layer (today) | Layered | Layered, `pkgs/` excluded |
|---|---|---|---|
| Conda layers | 1 × 1977 MB | 20, largest 399 MB | 19, largest 399 MB |
| Image total (compressed) | 2013 MB | 2013 MB | 1859 MB |
| `/opt/conda` listing | reference | identical | identical minus `pkgs/` |
| Layer export | 101 s | 16 s | — |

## Files Changed

| Category | Files |
|----------|-------|
| API Models | `BuildTemplate.java` |
| Configuration | `BuildConfig.groovy`, `application.yml` |
| Templates | `conda-micromamba-v3/dockerfile-conda-file.txt` |
| Helpers | `TemplateUtils.java`, `CondaHelper.groovy`, `ContainerHelper.groovy` |
| Controller | `ContainerController.groovy` |
| Services | `ContainerInspectServiceImpl.groovy` |
| Tool | `condasplit/` (Go sources and tests, `frontend/` module, `Dockerfile`, `Makefile`, `README.md`) |
| CI | `.github/workflows/build-condasplit.yml` |
| Tests | `*Test.groovy` for all modified components, golden files for the existing templates |
| Docs | `api.md`, `features/container-builds.mdx`, `cli/use-cases.md`, `install/reference.md` |

## Consequences

### Positive
- Conda layers stay under 512 MB compressed, so images work with registries, proxies and caches that limit blob size
- Container runtimes can download and unpack the environment in parallel
- Faster layer export (101 s to 16 s for the reference environment)
- Smaller images, since the package cache is dropped
- Container content is unchanged from `conda/micromamba:v2`, and the build log explains the layering
- Existing templates, the default template and existing container ids are untouched

### Negative
- First Go code in the repository: CI needs a Go toolchain for the tool's tests
- A new tool image to publish, version and, for Enterprise installs, mirror
- The frontend depends on `github.com/moby/buildkit`, which must be upgraded together with the production BuildKit
- A single file larger than 500 MB still produces a blob over 512 MB
- The install step is still exported to the build cache as one large blob, as with `conda/micromamba:v2`
- No sharing of identical package layers across images, and layer digests are not byte-reproducible
- A custom `mambaImage` with a root prefix other than `/opt/conda` is not supported, as with `conda/micromamba:v2`

### Neutral
- `conda/micromamba:v2` remains the default template
- Tuning the grouping parameters requires a new template version
- A tool upgrade changes container ids of `conda/micromamba:v3` images only
- The same tool could later serve `conda/pixi` environments, which also have `conda-meta/`

## Follow-ups

- **Production default**: At the production release, switch the default of `wave.build.condasplit-image` to `public.cr.seqera.io/wave/condasplit:v1` in `BuildConfig.groovy`, `application.yml` and `docs/install/reference.md`
- **Nextflow**: Add `conda/micromamba:v3` to the `wave.build.template` description in `plugins/nf-wave/src/main/io/seqera/wave/plugin/config/WaveConfig.groovy`, and correct its stated default to `conda/micromamba:v2`
- **Wave CLI**: Add `conda/micromamba:v3` to the `--build-template` help text in `app/src/main/java/io/seqera/wave/cli/App.java`

## References

- [Feature specification](../specs/002-conda-micromamba-v3-layers/spec.md)
- [Acceptance and verification results](../specs/002-conda-micromamba-v3-layers/spec.md#acceptance-and-verification)
- [Multi-Stage Build Templates ADR](20251203-multi-stage-build-templates.md)
- [Layered Conda builds API documentation](../docs/api.md#layered-conda-builds)
- [Dockerfile `RUN --mount`](https://docs.docker.com/reference/dockerfile/#run---mount)
- [Dockerfile `COPY --link`](https://docs.docker.com/reference/dockerfile/#copy---link)

---

**Status:** Implemented (branch 002-conda-micromamba-v3-layers)
**Date:** 2026-09-30
**Authors:** Wave Team
