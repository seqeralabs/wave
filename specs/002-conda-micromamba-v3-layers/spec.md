# Feature Specification: Layered Conda Builds (`conda/micromamba:v3`)

**Feature Branch**: `002-conda-micromamba-v3-layers`
**Created**: 2026-09-30
**Status**: Draft
**Input**: User description: "Explore how containers built by Wave using a conda recipe could use a separate layer for each tool/dependency instead of one big fat layer. Ideally each layer should be < 512 MB. Only for Docker. Use a new versioned build template so current images will not be touched."

**Deployment Scope**: Wave service (Seqera Cloud and Enterprise). Docker (OCI) image format only.

**Affected Repositories**:
- `wave` (this repository): service, `wave-api` module, new `condasplit/` Go tool and image, docs
- `nextflow`, `wave-cli`: documentation / help text only. Both already pass `buildTemplate` through as free text, so no code change is needed to use the new template.

**References**:
- Proof of concept run on 2026-09-30 against build [`bd-edb12e4f0bf02cd3_1`](https://wave.seqera.io/view/builds/bd-edb12e4f0bf02cd3_1) (gatk4 4.6.2.0 + gcnvkernel 0.9). Results in [Appendix A](#appendix-a-proof-of-concept-results).
- ADR [`adr/20251203-multi-stage-build-templates.md`](../../adr/20251203-multi-stage-build-templates.md), which introduced versioned build templates.

---

## Summary

Add a new, opt-in conda build template, `conda/micromamba:v3`. It installs the environment exactly like `conda/micromamba:v2`. Then, in the same build step, a small static Go tool (`condasplit`) moves the installed files from `/opt/conda` into up to 32 layer directories, grouped by conda package, with each layer at most 500 MB before compression. The final stage adds each directory as its own image layer.

The tool ships as a `FROM scratch` image under the same path as the other Wave tool images: `public.cr.stage-seqera.io/wave/condasplit` for the initial iteration and tests, and `public.cr.seqera.io/wave/condasplit` for production. It is mounted into the build step and never copied into the image. The container's file content is the same as v2, except the unused package cache (`/opt/conda/pkgs`) is dropped.

Existing templates (`conda/micromamba:v1`, `conda/micromamba:v2`, `conda/pixi:v1`, `cran/installr:v1`) and the default template are not modified, so existing container ids, cached images and community registry tags stay as they are.

## Context

Every conda template today installs the whole environment with one `RUN` and ships it as one layer. For v2, `src/main/resources/templates/conda-micromamba-v2/dockerfile-conda-file.txt` ends with:

```dockerfile
COPY --from=build "$MAMBA_ROOT_PREFIX" "$MAMBA_ROOT_PREFIX"
```

For the reference environment (322 packages, linux/amd64) that single layer is **1977 MB compressed**. This causes three problems:

1. **Size limits (primary driver).** Some registries, proxies and CDN caches reject or refuse to cache blobs larger than 512 MB.
2. **Pull speed (secondary driver).** Container runtimes download several layers at a time. A single 2 GB layer is fetched over one connection and unpacked by one thread.
3. **Build export time.** BuildKit compresses layers in parallel, one per core. A single layer takes 101 s to compress for the reference image, compared with 16 s for the layered version.

## Goals

- G1: Every conda layer of a `conda/micromamba:v3` image is under 512 MB compressed. The one exception is a single file larger than the limit, which cannot be split.
- G2: Large packages get their own layer; small packages are grouped together.
- G3: The container content is identical to what `conda/micromamba:v2` produces for the same environment, minus `/opt/conda/pkgs`.
- G4: No change to existing templates, the default template, or any existing container id.
- G5: No new services, persistence, or API fields beyond one new template value and one config property.
- G6: Build disk usage and build cache size no larger than v2's.

## Non-Goals

- Singularity / SIF output. A SIF image is a single squashfs file, so layering doesn't apply. Requests fail with HTTP 400.
- Changing `conda/micromamba:v1`, `conda/micromamba:v2` or `conda/pixi:v1`, including dropping the package cache from them. That would change every existing container id.
- Making v3 the default template.
- Pixi and CRAN templates. The same tool could later serve `conda/pixi` (pixi environments also have `conda-meta/`), but that is out of scope here.
- Sharing identical package layers across different images, or byte-reproducible layer digests.
- Showing the layer plan in the UI as structured data. It is plain build-log output only.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Opt-in layered build (Priority: P1)

A Nextflow user whose images are too large for their registry's blob limit sets `wave.build.template = 'conda/micromamba:v3'`. Wave builds the conda environment as multiple layers, each under the limit.

**Why this priority**: This is the feature.

**Independent Test**: Submit a container request with `packages` (type CONDA) and `buildTemplate: "conda/micromamba:v3"`, then inspect the pushed manifest.

**Acceptance Scenarios**:

1. **Given** a request with `buildTemplate: "conda/micromamba:v3"` and a conda environment, **When** the build completes, **Then** the image manifest contains the base image layers, 32 conda slot layers, and any layers from custom `commands`.
2. **Given** the reference environment ([Appendix A](#appendix-a-proof-of-concept-results)), **When** built with v3, **Then** no conda layer is larger than 512 MB compressed, and packages of at least 50 MB (such as `gatk4`, `openjdk`, `pytorch`) each occupy their own layer.
3. **Given** a v3 request with a remote conda lock file URL in `packages.entries`, **When** the build runs, **Then** the environment is installed from the lock file and layered the same way.
4. **Given** a v3 multi-platform request (`linux/amd64,linux/arm64`), **When** the build runs, **Then** each platform image is layered independently, using the matching tool binary, and the index is assembled as today.

---

### User Story 2 - Existing images untouched (Priority: P1)

Operators upgrade Wave, and nothing changes for anyone who doesn't opt in.

**Why this priority**: Changing an existing template's output changes the container id of every image built from it, which triggers rebuilds and new tags in the community registry.

**Independent Test**: Render every existing template before and after the change and compare the output byte for byte.

**Acceptance Scenarios**:

1. **Given** a request with no `buildTemplate`, or with `conda/micromamba:v1`, `conda/micromamba:v2` or `conda/pixi:v1`, **When** Wave generates the container file, **Then** the output is byte-identical to the previous Wave release.
2. **Given** an existing image in the build cache or community registry, **When** the same request is repeated after the upgrade, **Then** Wave returns the same container id and does not rebuild.

---

### User Story 3 - Same container content (Priority: P1)

A user switching from v2 to v3 gets the same tools, versions and files.

**Why this priority**: Layering must be invisible to the workload.

**Independent Test**: Build the same lock file with v2 and v3, list `/opt/conda` in both images (path, type, size, mode, owner, symlink target), and diff the listings. Compare hardlinks by grouping paths per inode in each image, not by raw link count.

**Acceptance Scenarios**:

1. **Given** the same environment built with v2 and v3, **When** the listings are compared, **Then** they are identical except that `/opt/conda/pkgs/**` is absent in v3.
2. **Given** a v3 image, **When** the environment's tools run (for the reference image: `gatk --version`, `python -c "import gcnvkernel"`), **Then** they behave exactly as in v2.
3. **Given** files that conda hardlinks together, **When** they are layered, **Then** they stay hardlinked: the sets of paths outside `pkgs/` that share an inode are the same as in v2. Link counts are lower than in v2 by the number of links that were in `pkgs/`, because those links stay in the build stage.
4. **Given** a v3 image, **When** its filesystem is searched for `condasplit`, **Then** the tool is not present.

---

### User Story 4 - Layer plan visible in the build log (Priority: P2)

A user or support engineer opens the build page and can see which packages went into which layer and how large each layer is.

**Why this priority**: Needed to diagnose oversized layers and to explain the result, but not required for the build itself.

**Acceptance Scenarios**:

1. **Given** a v3 build, **When** the build log is viewed, **Then** it contains a layer plan between `>> CONDA_LAYERS_START` and `<< CONDA_LAYERS_END`: one row per used slot with its size, file count and package names.
2. **Given** a v3 build, **When** the conda lock is extracted from the log, **Then** extraction works exactly as for v2. The lock markers are unchanged and printed before the layer plan.

---

### User Story 5 - Oversized content and slot overflow (Priority: P2)

Environments that cannot fit the normal rules still build, with clear warnings.

**Acceptance Scenarios**:

1. **Given** a package larger than 500 MB, **When** it is sorted, **Then** its files are split across several layers of at most 500 MB each.
2. **Given** a single file larger than 500 MB, **When** it is sorted, **Then** it gets a layer to itself and the plan shows `WARN oversized file <path> <size>`.
3. **Given** an environment that needs more than 32 layers, **When** it is sorted, **Then** the two smallest layers are merged repeatedly until 32 remain, and the plan shows `WARN slots exceeded: merged <n> layers`.
4. **Given** any of the warnings above, **When** the build finishes, **Then** the build still succeeds.

---

### User Story 6 - Singularity rejected (Priority: P1)

**Acceptance Scenarios**:

1. **Given** a request with `buildTemplate: "conda/micromamba:v3"` and `format: "sif"`, **When** submitted, **Then** Wave returns HTTP 400 with the message `Build template 'conda/micromamba:v3' does not support Singularity format`.
2. **Given** a v3 request with a non-CONDA package type, **When** submitted, **Then** Wave returns HTTP 400 with the message `Package type '<type>' not supported by 'conda/micromamba:v3' build template`, as v2 does today.

---

### Edge Cases

- **Empty or tiny environment.** Only a few slots are used. Unused slots produce the standard 32-byte empty layer (`sha256:4f4fb700ef54…`), which most base images already contain.
- **Files not claimed by any package.** Examples are `conda-meta/history`, `.messages.txt` and files created by post-link scripts. They go into a trailing leftovers layer, which is also capped at 500 MB.
- **Path claimed by two packages (clobbering).** The file is placed once, with the owner taken from the first `conda-meta` file in sorted order. The on-disk content is always the final installed version, so layer order never matters.
- **Symlinks, directory symlinks and empty directories.** They are preserved as-is. An originally empty directory is recreated in the leftovers layer.
- **Hardlinked files across packages.** All paths that share one inode are placed in the same layer, the one belonging to the owner of the first owned path.
- **Files from lower image layers.** A custom `mambaImage` may already contain files under `/opt/conda` in its own layers. The default images contain only an empty base environment. Moving such a file makes overlayfs copy it up first. This keeps its metadata but breaks hardlinks between such pre-existing files, because each link is copied up on its own. `rename()` never fails with `EXDEV` there: source and destination are in the same root filesystem, and only directories could need it, which the tool creates instead of moving.
- **Custom `condaOpts.commands`.** They are appended after the slot `COPY` lines, as in v2, and produce their own layers.
- **Custom `baseImage` / `containerImage`.** They are supported as in v2. The layer carries `/opt` and `/opt/conda`, both 0755 root:root as in the v2 image. Only the prefix's timestamps are kept from the build stage.
- **Custom `mambaImage` with a root prefix other than `/opt/conda`.** This is not supported. v2 already hard-codes `/opt/conda` in its final stage, and v3 keeps the same assumption.
- **CUDA retry path.** The `__cuda` retry with `CONDA_OVERRIDE_CUDA` is kept unchanged from v2. The tool runs only after a successful install.

---

## Design

### Overview

A v3 build is one BuildKit build of a two-stage Dockerfile. Only stage 2 is shipped.

```
                         <registry>/wave/condasplit:vN   (FROM scratch, one static binary)
                                            │ RUN --mount (read-only, never written to disk)
                                            ▼
            ┌────────────────────────────────────────────────┐   COPY --link ×32    ┌───────────────────────┐
conda.yml ─▶│ build   {{mamba_image}}                        │ ───────────────────▶ │ prod  {{base_image}}  │ ─▶ registry
            │  1. micromamba install → /opt/conda  (as v2)   │   /layers/NN/ → /    │ base + 32 slot layers │
            │  2. print conda lock                  (as v2)  │                      └───────────────────────┘
            │  3. condasplit: move /opt/conda → /layers/NN   │
            └───────────────┬────────────────────────────────┘
                            │ build log: conda lock, layer plan
```

1. **build**: runs v2's install commands and prints the conda lock between the existing markers. A lock-file URL is first added with `ADD` and installed from the local copy (see the lock-file URL variant below). In the same `RUN`, it then runs `condasplit` from the mounted tool image. The tool moves every file of `/opt/conda` (except `pkgs/`) into `/layers/00` … `/layers/31`, each rooted at `/`, and prints the layer plan.
2. **prod**: starts from `{{base_image}}` and copies each layer directory with `COPY --link`. Unused slots are empty directories and yield the standard empty layer.

**Why move instead of copy?** The install and the tool run in the same `RUN`, so every installed file is in the same writable layer and `rename()` only updates directory entries. This has four effects:
- Build disk usage and build cache size stay at v2's level.
- Hardlinks, owners, modes and timestamps survive automatically, because a moved file is the same file.
- The step takes seconds.
- The tool can check it placed everything: after the move, `/opt/conda` must contain only directories and the excluded `pkgs/`.

### Why a fixed number of slots

Wave generates the Dockerfile before the environment is solved, so the number of layers is not known in advance. The template therefore declares a fixed set of 32 `COPY` slots, and the tool decides what each slot contains. This keeps the whole build in a single Dockerfile, with no extra solve job and no custom BuildKit frontend. The POC confirmed that an empty slot costs 32 bytes and a registry round trip for a blob that is already cached.

32 slots allow up to about 16 GB of environment before compression at 500 MB per slot. With a typical base image (ubuntu:24.04 has one layer) the image stays around 35 layers, well under the ~125-layer overlay limit.

### Template: `conda-micromamba-v3/dockerfile-conda-file.txt`

This is v2's stage 1 plus the tool call, and a different final stage. The slot lines are written out literally in the template file, so the file shows exactly what gets built.

```dockerfile
FROM {{mamba_image}} AS build
USER root
COPY --chown=$MAMBA_USER:$MAMBA_USER conda.yml /tmp/conda.yml
# expose `which` at /usr/bin/which for R (bioconda) post-link scripts; the amazon2023 base image lacks it
# the condasplit tool is mounted read-only for this step only and never ends up in the image
RUN --mount=type=bind,from={{layers_image}},source=/,target=/opt/wave-tools \
    micromamba install -y -n base conda-forge::which \
    && ln -sf "$MAMBA_ROOT_PREFIX/bin/which" /usr/bin/which \
    && (micromamba install -y -n base -f /tmp/conda.yml > /tmp/mamba.log 2>&1 \
    && cat /tmp/mamba.log \
    || (cat /tmp/mamba.log >&2 && grep -q __cuda /tmp/mamba.log \
        && CONDA_OVERRIDE_CUDA="99" micromamba install -y -n base -f /tmp/conda.yml)) \
    {{base_packages}}
    && micromamba clean -a -y \
    && micromamba env export --name base --explicit > environment.lock \
    && echo ">> CONDA_LOCK_START" \
    && cat environment.lock \
    && echo "<< CONDA_LOCK_END" \
    && /opt/wave-tools/condasplit --src /opt/conda --out /layers \
        --slots 32 --max-layer-size 500000000 --own-layer-size 50000000 --exclude pkgs

FROM {{base_image}} AS prod
ARG MAMBA_ROOT_PREFIX="/opt/conda"
ENV MAMBA_ROOT_PREFIX=$MAMBA_ROOT_PREFIX
COPY --link --from=build /layers/00/ /
COPY --link --from=build /layers/01/ /
# ... one line per slot ...
COPY --link --from=build /layers/31/ /
USER root
ENV PATH="$MAMBA_ROOT_PREFIX/bin:$PATH"
```

`conda-micromamba-v3/dockerfile-conda-packages.txt` (the lock-file URL variant) mirrors v2's `dockerfile-conda-packages.txt` (`{{channel_opts}} {{target}}`) with one difference. micromamba downloads `-f <url>` only for a YAML environment file, and treats an explicit-format lock URL as a local path. Wave's `/v1alpha1/builds/<id>/condalock` endpoint serves the explicit format. So when the entry is an `http(s)` URL, the build stage first adds the file with `ADD <url> /tmp/conda-lock/<name>`, and both install commands (the normal one and the CUDA retry) use `-f /tmp/conda-lock/<name>`. `<name>` is the last segment of the URL path without query or fragment, because micromamba picks the file format from the extension. It falls back to `conda.lock` when the segment is empty or has unusual characters. The lock stays in the build stage and never reaches the final image. v2 has the same limitation with explicit lock URLs but is left unchanged, so its container ids stay the same. The tool call and the final stage are identical in both files.

The tool's parameters are literal in the template. Changing any of them changes the container file, and therefore the container id, which is the intended behaviour. Tuning them later means a new template version.

Units throughout are decimal: 1 MB = 1,000,000 bytes.

### Tool: `condasplit`

A small Go program built as a static binary (`CGO_ENABLED=0`) using only the Go standard library. It has no runtime dependencies, so it runs inside any `{{mamba_image}}`, whatever its Linux distribution. The grouping rules are the ones validated by the POC splitter ([Appendix A](#appendix-a-proof-of-concept-results)).

**CLI**

```
condasplit --src DIR --out DIR --slots N
             [--max-layer-size BYTES] [--own-layer-size BYTES] [--exclude RELPATH ...]
```

| Option | Default | Meaning |
|---|---|---|
| `--src` | (required) | Absolute path of the installed prefix, e.g. `/opt/conda`. The same path is used inside each layer. |
| `--out` | (required) | Output root. The tool creates `NN/` for every slot `00` … `N-1` |
| `--slots` | (required) | Number of slots declared by the template |
| `--max-layer-size` | 500000000 | Cap per layer: the sum of file sizes before compression |
| `--own-layer-size` | 50000000 | Packages at least this size get a layer to themselves |
| `--exclude` | none | Paths relative to `--src` that are left in place and not layered (v3 passes `pkgs`) |

**Algorithm**

1. **Inventory.** Walk `--src` without following symlinks, skipping `--exclude` subtrees. Record every regular file, symlink (including symlinks to directories) and directory, with its `lstat` metadata. Mark directories that are empty.
2. **Atoms.** Group regular files by `(dev, inode)`. Each group is one atom whose size counts once. Every symlink and empty directory is an atom of size 0. An atom is never split across layers, which keeps hardlinks intact.
3. **Ownership.** Parse each `conda-meta/<dist>.json` with `encoding/json`, sorted by file name. Claim every path in its `files` list plus the JSON file itself for package `name`. The first claim wins, and later claims are counted as clobbers. An atom belongs to the owner of its first owned path, in sorted order. Unowned atoms go to a set called `rest`.
4. **Units.** A package's atoms form one unit. A unit larger than `--max-layer-size` is cut into chunks of at most the cap, taking atoms in path order. An atom larger than the cap becomes its own chunk and is flagged as an oversized file.
5. **Packing.** Units at least `--own-layer-size` each become a layer. The remaining units are packed first-fit-decreasing, by size and then name, into shared layers of at most the cap. `rest` is chunked into trailing layers of at most the cap.
6. **Slot fitting.** While there are more layers than `--slots`, merge the two smallest layers and record a `slots exceeded` warning. The result may exceed the cap, in which case a warning is also recorded.
7. **Move.** For each layer *i*:
   - Create `--out/NN/<src>/` with mode 0755 and the times of `--src`, owned by root:root when running as root. This matches the v2 image, where `/opt/conda` is 0755 root:root whatever the prefix mode in the mamba image (0777). Parents of `<src>` get mode 0755 root:root.
   - `rename()` each path of each atom to `--out/NN/<src>/<relpath>`, creating missing parent directories with the metadata of the source directory.
   - Recreate originally empty directories in the leftovers layer.
   - Finally, set directory mtimes from the inventory.
   - Leave unused slot directories empty.
8. **Verify.** Walk `--src` again. Anything other than directories and excluded paths is an error, and the tool fails listing up to 20 of the offending paths.
9. **Plan.** Print the layer plan to stdout between `>> CONDA_LAYERS_START` and `<< CONDA_LAYERS_END`. The example below uses the reference environment's numbers:

```
>> CONDA_LAYERS_START
slots=19/32 files=88231 size=4505.0MB packages=323 clobbers=28 unowned=0.0MB max-layer=500MB own-layer=50MB
00  454.5MB     29  mkl#0
01  425.8MB      4  gatk4
...
15  500.0MB   8160  libstdcxx-devel_linux-64 binutils_impl_linux-64 r-base icu scikit-learn gxx_impl_linux-64 +9
18    0.0MB      3  (unowned)
WARN oversized file lib/libexample.so 612.4MB   # illustrative; the reference environment has none
<< CONDA_LAYERS_END
```

**Guarantees**

- **Deterministic.** The same prefix content always gives the same plan, because everything is sorted and nothing is random.
- **Complete.** Every inventoried path ends up in exactly one layer, enforced by the verify step.
- **Exit codes.** `0` on success, including when warnings were printed. Non-zero on a missing or relative `--src`, a missing `conda-meta/`, a JSON parse error, an I/O error, or a failed verify step, with a one-line error message on stderr. A non-zero exit fails the build like any other `RUN` failure.

### Tool image: `wave/condasplit`

**Registries.** The initial iteration and all tests use `public.cr.stage-seqera.io/wave/condasplit`. The production registry `public.cr.seqera.io/wave/condasplit` is used only for the production release (see [Rollout](#rollout)).

This is a new top-level folder, `condasplit/`, following the pattern of the existing Wave tool images such as `scanner/`:

| File | Purpose |
|---|---|
| `condasplit/go.mod`, `*.go` | The tool (standard library only) |
| `condasplit/*_test.go` | Unit tests (`go test ./...`) |
| `condasplit/Dockerfile` | Multi-stage build: `FROM --platform=$BUILDPLATFORM golang:1.27` (pinned) cross-compiles for `$TARGETARCH` and runs `go test`. `FROM scratch` then contains only `/condasplit`. |
| `condasplit/Makefile` | `docker buildx build --push --platform linux/amd64,linux/arm64 --tag ${registry}/condasplit:${version} .`, with `registry ?= public.cr.stage-seqera.io/wave` (production: `make all registry=public.cr.seqera.io/wave`) |
| `condasplit/README.md` | What the tool does and how to release it |
| `.github/workflows/build-condasplit.yml` | Manual (`workflow_dispatch`) production publish job running `make all version=… registry=public.cr.seqera.io/wave`, modeled on `build-plugin-scanner.yml`, plus a `go test` job on pull requests touching `condasplit/**` |

```dockerfile
FROM --platform=$BUILDPLATFORM golang:1.27 AS build
ARG TARGETARCH
WORKDIR /src
COPY . .
RUN go test ./... \
 && CGO_ENABLED=0 GOOS=linux GOARCH=$TARGETARCH go build -trimpath -ldflags="-s -w" -o /condasplit .

FROM scratch
COPY --from=build /condasplit /condasplit
```

**How the binary reaches the build.** The v3 Dockerfile names the image in `RUN --mount=type=bind,from={{layers_image}},…`. BuildKit resolves the variant for the build's platform, pulls its single layer (a few MB), caches it on the builder, and mounts it read-only for the one `RUN`. It is pulled the same way `{{mamba_image}}` is, with the configured registry credentials.

Wave schedules each platform's build on nodes of that architecture (node selector per platform), so the tool always runs natively. The binary is never written into any stage's filesystem, so it is absent from both the final image and the build cache.

**Versioning.** Each release gets a new immutable tag (`v1`, `v2`, …). Wave pins the exact tag in config, so a tool change always changes the v3 container file and container id. The config value may also pin a digest (`…/condasplit:v1@sha256:…`).

### Wave service changes

| Area | Change |
|---|---|
| `wave-api` `BuildTemplate` | Add `CONDA_MICROMAMBA_V3 = "conda/micromamba:v3"`. `defaultTemplate()` is unchanged and still returns v2. |
| `BuildConfig` | Add `@Value('${wave.build.condasplit-image:`public.cr.stage-seqera.io/wave/condasplit:v1`}') String condasplitImage` and include it in the config log line. The default points to the stage registry for the initial iteration and switches to `public.cr.seqera.io/wave/condasplit:v1` at the production release. |
| `application.yml` | Document `wave.build.condasplit-image` next to `buildkit-image` and `singularity-image`. |
| `TemplateUtils` | Add `condaFileToDockerFileUsingV3(CondaOpts, String layersImage)` and `condaPackagesToDockerFileUsingV3(String packages, List<String> channels, CondaOpts, String layersImage)`. They render the v3 templates with the v2 bindings plus `layers_image`, and they reuse `addCommands`. |
| `CondaHelper` | Add `containerFileV3(PackagesSpec spec, String containerImage, String layersImage)`. It applies the same CONDA-type check, lock-file detection, v1-image override and `containerImage → baseImage` handling as `containerFileV2`, but has no Singularity branch. |
| `ContainerHelper.containerFileFromRequest` | Add a `String condasplitImage` parameter. Route `CONDA_MICROMAMBA_V3` to `CondaHelper.containerFileV3`. When the format is Singularity, throw `BadRequestException("Build template 'conda/micromamba:v3' does not support Singularity format")`. The branches for other templates are unchanged. |
| `ContainerController` | Pass `buildConfig.condasplitImage` to `containerFileFromRequest`. |
| `ContainerInspectServiceImpl` | `findRepositories` also collects images referenced by `RUN --mount=...,from=<image>` (build stage names are ignored), so the tool image gets registry credentials like `FROM` images. |
| Templates | Add `src/main/resources/templates/conda-micromamba-v3/dockerfile-conda-file.txt` and `dockerfile-conda-packages.txt`. There are no Singularity files. |

These components need no changes: `BuildStrategy` (buildctl args, cache options, compression), `BuildLogServiceImpl` (lock markers are unchanged), persistence (`buildTemplate` is already stored and shown on the build page), `MultiPlatformBuildService`, scanning, mirroring and freeze.

The generated Dockerfile (about 3 KB) is not affected by `wave.build.max-container-file-size`. That limit is checked on user-submitted container files before Wave generates its own.

### Compatibility and container ids

`makeContainerId` hashes the generated container file among other inputs. v3 produces a different container file, so it gets different container ids and never collides with images built by other templates. Existing templates' output is unchanged, so their ids are unchanged. The tool image reference is part of the v3 container file, so upgrading the tool yields new ids for v3 images only.

### Documentation

- `docs/api.md`: add `conda/micromamba:v3` to the `buildTemplate` row and add a request example.
- `docs/features/container-builds.mdx` and `docs/cli/use-cases.md`: describe v3, when to use it, and that it is Docker-only.
- `nextflow` `plugins/nf-wave/.../WaveConfig.groovy`: add v3 to the `wave.build.template` description.
- `wave-cli` `App.java`: add v3 to the `--build-template` help text.
- Add an ADR in `adr/` once implemented, as for the multi-stage templates.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Wave MUST accept `buildTemplate: "conda/micromamba:v3"` for CONDA package requests in Docker format.
- **FR-002**: Wave MUST reject v3 with Singularity format, and v3 with non-CONDA package types, with HTTP 400.
- **FR-003**: The container file produced for any template other than v3 MUST be byte-identical to the previous release.
- **FR-004**: The v3 build MUST install the environment with the same commands as v2, including base packages, the CUDA retry and the lock-file markers. The one exception is a lock-file URL, which v3 first adds to the build stage with `ADD` and installs from the local path, because micromamba can't read an explicit lock file from a URL.
- **FR-005**: The v3 image MUST contain every path of the installed prefix except `pkgs/`, with the same type, size, mode, owner, symlink target and hardlink grouping.
- **FR-006**: Each conda layer MUST be at most 500 MB before compression. The only exceptions are an atom that is individually larger (a single file or hardlink group) and layers merged because of slot overflow. Both cases MUST produce a warning in the build log.
- **FR-007**: Packages of at least 50 MB MUST each occupy their own layer or layers, unless merged because of slot overflow.
- **FR-008**: Packages larger than 500 MB MUST be split across several layers.
- **FR-009**: The build log MUST contain the layer plan between `>> CONDA_LAYERS_START` and `<< CONDA_LAYERS_END`.
- **FR-010**: The tool MUST be mounted into the build step, not copied, and MUST NOT be present in the final image.
- **FR-011**: The tool image MUST be configurable with `wave.build.condasplit-image`, so that Enterprise installs can mirror it.
- **FR-012**: v3 MUST support the conda-file and lock-file-URL inputs, custom `baseImage`/`containerImage`, `mambaImage`, `basePackages` and `commands`, as v2 does.
- **FR-013**: v3 MUST work for single-platform and multi-platform (`linux/amd64,linux/arm64`) builds.
- **FR-014**: The tool MUST fail the build if any non-directory path is left under `--src` (other than excluded paths) after moving.

### Non-Functional Requirements

- **NFR-001**: The tool SHOULD complete in under 30 s for a 5 GB / 180,000-file prefix on a build node. Moving files only renames them, so the expected time is a few seconds.
- **NFR-002**: End-to-end build time of v3 SHOULD NOT exceed v2 for the same environment.
- **NFR-003**: Build disk usage and exported build cache size of v3 SHOULD NOT exceed v2 for the same environment.
- **NFR-004**: The tool MUST be a static binary with no runtime dependencies, built from the Go standard library only. The image MUST be `FROM scratch`, multi-arch (amd64, arm64), and built from a pinned Go toolchain image.

---

## Success Criteria *(mandatory)*

- **SC-001**: For the reference environment ([Appendix A](#appendix-a-proof-of-concept-results)), the largest compressed conda layer is under 512 MB. The POC's largest was 399 MB.
- **SC-002**: For the reference environment, the v3 listing of `/opt/conda` equals the v2 listing minus `pkgs/`: 0 differing paths and the same hardlink grouping, meaning the same sets of paths share an inode. Link counts drop by the number of links that were in `pkgs/`. Directory link counts are not compared, because overlayfs reports `nlink=1` for a directory whose entries come from more than one layer.
- **SC-003**: Golden-file tests show unchanged output for all existing templates.
- **SC-004**: v3 total image size is at most v2's for the same environment. The POC measured 1859 MB against 2013 MB.
- **SC-005**: In a small environment (such as `bioconda::samtools`), every unused slot is the 32-byte empty layer `sha256:4f4fb700ef54461cfa02571ae0db9a0dc1e0cdb5577484a6d75e68dc38e8acc1`.
- **SC-006**: The tool image is under 10 MB per architecture.

---

## Testing Strategy

**Unit tests (Wave, Spock)**
- `TemplateUtilsTest`: render both v3 templates. Assert the bindings (`mamba_image`, `base_image`, `layers_image`, `base_packages`, `channel_opts`/`target`), the `RUN --mount` line, exactly 32 `COPY --link` slot lines, and `commands` appended at the end.
- `CondaHelperTest`: cover `containerFileV3` for a conda file, a lock URL, a `containerImage` override, a v1 `mambaImage` override, and a non-CONDA type (which fails).
- `ContainerHelperTest`: v3 routes to `containerFileV3`. v3 with Singularity returns 400. Golden-file output for no template, v1, v2, pixi and cran is unchanged.
- `BuildConfigTest`: default value and override of `condasplit-image`.

**Unit tests (`condasplit`, `go test`)**, using synthetic prefixes in `t.TempDir()`. Owner tests need root and run in the image build; they are skipped otherwise.
- Ownership from `conda-meta`, unowned files going to `rest`, and first-wins clobbering.
- Hardlinks within one package, across a package and an excluded path, and across two packages.
- Symlinks, directory symlinks and originally empty directories.
- A package larger than the cap being split, and a single file larger than the cap being isolated with a warning.
- Packing that respects the cap, the own-layer threshold, and deterministic output.
- Slot overflow merging with a warning.
- `--exclude pkgs` leaving the path in place.
- Metadata preservation (mode, uid/gid, file and directory mtimes) and prefix/parent metadata.
- The verify step failing when a path is left behind.
- Error exits for a missing `conda-meta/`, a malformed JSON file and a relative `--src`.

**Integration (manual or CI job, BuildKit `v0.25.2-rootless` as in production)**
- Build the reference lock with v2 and v3, then check SC-001, SC-002 and SC-004 with `crane manifest` and a `find -printf` listing diff. For hardlinks, group paths by inode (`%i`) in each image, drop `pkgs/` paths, and compare the groups. Leave raw link counts (`%n`) out of the listing diff, since they differ by design: file link counts drop by the `pkgs/` links, and overlayfs reports `nlink=1` for directories merged across layers. Run `gatk --version` and `python -c "import gcnvkernel"`. Confirm `condasplit` is absent from the image.
- Build a small environment, then check SC-005.
- Build an arm64 image on an arm64 node.
- Compare the build cache size and peak build disk usage of v2 and v3 (NFR-003).
- Validate in staging with Nextflow `wave.build.template = 'conda/micromamba:v3'`.

---

## Rollout

1. Build and push `public.cr.stage-seqera.io/wave/condasplit:v1` (multi-arch) from `condasplit/` with `make all`. Wave's default for `wave.build.condasplit-image` points there during the initial iteration.
2. Validate in staging, including the reference environment and rootless BuildKit.
3. Publish `public.cr.seqera.io/wave/condasplit:v1` with the `build-condasplit.yml` workflow and switch the default of `wave.build.condasplit-image` to it.
4. Release Wave with the template, config default and docs. Nothing changes for existing users.
5. Announce as opt-in. Enterprise installs mirror the tool image and set `wave.build.condasplit-image` if they have no access to `public.cr.seqera.io`.
6. Possible follow-up, not part of this spec: consider making v3 the default in a later major version, as a separate decision.

---

## Risks and Open Questions

| # | Topic | Detail | Proposed handling |
|---|---|---|---|
| R1 | Move-based layering not yet exercised | The POC copied files into a separate stage. Moving files inside the install step is new. | The integration test repeats the POC listing diff (SC-002) against the v2 output. |
| R2 | Build cache blob size | As with v2, the install step is exported to the build cache as one large blob (larger than 512 MB). Moving files doesn't change its size. | Confirm whether the cache repository is subject to the 512 MB limit. It is internal, and `ignore-error=true` already keeps the build from failing. |
| R3 | Rootless BuildKit | The POC ran rootful BuildKit v0.25.2. Production uses `v0.25.2-rootless`. | The integration test must run rootless and verify owner, mode and hardlinks. |
| R4 | Single files over 512 MB | Some GPU libraries exceed the limit on their own. They get their own layer, but the image still has a blob over 512 MB. | **Decided:** the build continues, and the layer plan in the build log shows `WARN oversized file <path> <size>`. |
| R5 | Slot overflow | The merge rule is designed but was not exercised by the POC. | Covered by the tool's unit tests. Add an integration check with a large environment. |
| R6 | Pull speed | Not measured against a remote registry. The POC's local pulls were equal (25–28 s). | Measure in staging: pull the reference image with v2 and v3 from the production registry. |
| R7 | Compressed size estimate | The cap is applied before compression. Tar headers and padding add up to about 1 KB per file. With 12 MB of headroom and gzip compressing padding to nearly nothing, the POC maximum was 399 MB. | Accept. If a guarantee is needed, add a post-push manifest check that logs a warning when a layer exceeds 512 MB. This is a follow-up, not part of this spec. |
| R8 | Go in the repository | This is the first Go code in the `wave` repo. CI needs a Go toolchain for the tool's tests. | The Go code is isolated in `condasplit/`. The toolchain comes from the pinned `golang` image, both in the Dockerfile and in the workflow's test job. That job runs `go vet` and `go test` as root in a `golang:1.27` container, so the owner tests run too. |

---

## Alternatives Considered

- **Change v2 in place.** Rejected: it changes every existing container id and conflicts with G4.
- **Tool language and packaging:**
  - *Python script in a `python:slim` image*: validated by the POC, but it brings a Python runtime and its CVEs into a build-time image.
  - *Bash script inline in the template*: nothing to publish, but it needs GNU `find`, `tar` and `awk`. The default v2 build image (`mambaorg/micromamba:2-amazon2023`) lacks `find` and `tar`. It would also parse the JSON receipts with awk, relying on micromamba's output layout.
  - *Java with a GraalVM native image*: same language as Wave, but a heavy toolchain for a small tool.
  - *Go binary bundled in the Wave jar and written into the build context*: no image to publish, but it adds about 10 MB to the jar, needs per-platform selection in Wave, and needs the version printed into the Dockerfile so container ids change.
  - The chosen tiny image follows the existing `public.cr.seqera.io/wave/` tool-image path.
- **Separate layering stage that copies instead of moves.** This is the POC layout. Rejected: it doubles the environment on disk and in the build cache.
- **Solve first, then generate an exact Dockerfile.** Run the solver (or use the lock) before generating the Dockerfile, so the layer count is exact and there are no empty slots. Rejected for now: it adds a solve job to the pipeline, and empty slots proved to cost nothing. Revisit if environments often need more than 32 layers.
- **Custom BuildKit (LLB) frontend.** Layers could be created dynamically, with no fixed slots. Rejected: it would be a new component to build and maintain, and would change the `buildctl` invocation.
- **Wave-managed per-package layer store (Nixery-style).** Rejected: it brings the most storage savings across images, but it is a new subsystem and isn't needed to meet the size limit.

---

## Appendix A: Proof-of-Concept Results

Reference environment: the conda lock of build `bd-edb12e4f0bf02cd3_1` (`bioconda::gatk4=4.6.2.0`, `bioconda::gcnvkernel=0.9`, 322 packages, linux/amd64). It was built on 2026-09-30 with BuildKit v0.25.2 (`docker-container` driver), gzip compression and OCI media types, and pushed to a local registry.

The POC differs from this design in three ways:
- It used a Python splitter in a separate stage that copied files instead of moving them.
- It ran stage 1 under amd64 emulation on an arm64 laptop.
- It used the v1 base image in the final stage.

It validated the grouping rules, the layer sizes, and that the image content, including hardlinks and metadata, is unchanged.

| | Single layer (today) | Layered | Layered, `pkgs/` excluded |
|---|---|---|---|
| Conda layers | 1 × 1977 MB | 20, max 399 MB | 19, max 399 MB |
| Image total (compressed) | 2013 MB | 2013 MB | 1859 MB |
| `/opt/conda` listing vs single layer | — | identical (198,913 paths, 149,640 hardlinked) | identical minus `pkgs/` |
| Build time after install | 108 s (export 101 s) | about 65 s (split 28 s, copies about 20 s, export 16 s) | — |

Layer plan of the "Layered" run (sizes before → after compression): mkl split into 454 MB → 146 MB and 343 MB → 120 MB; gatk4 426 → 399; pytorch 414 → 103; gcnvkernel 398 → 123; openjdk 460 → 237; qt-main 266 → 87; and nine more packages from 211 MB down to 51 MB. The rest are packed into three shared layers: 14 packages → 173 MB, 53 → 171 MB, 240 → 81 MB. A leftovers layer holds 0.1 MB.

Findings that shaped this design:
1. Micromamba's `clean -a` keeps extracted packages in `pkgs/`, and they are hardlinked to the environment. Splitting the pair doubled the content: the first attempt produced a 4.4 GB leftover layer. v3 excludes `pkgs/`, and the tool keeps every hardlink group in one layer.
2. Copying slot directories to `/opt/conda/` reset the prefix's mode and made each empty slot a slightly different tiny layer. Rooting layers at `/` and carrying the prefix directory with the same fixed metadata in every layer fixes both.
3. The copy-based split took 21–28 s and doubled the environment on disk. That motivated moving files inside the install step.
