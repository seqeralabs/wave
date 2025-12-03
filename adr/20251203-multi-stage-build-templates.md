# Multi-Stage Build Templates for Container Provisioning

## Summary

This document describes the implementation of multi-stage build templates in Wave, introducing support for Pixi (`pixi/v1`) and enhanced Micromamba (`micromamba/v2`) templates. These templates produce smaller, optimized container images by separating build-time dependencies from runtime environments.

## Context

Wave's default container builds using Micromamba v1 include the package manager and build tools in the final image. This results in larger images and potential security concerns from unnecessary binaries. Additionally, users requested support for Pixi, a modern and faster package manager from prefix.dev.

## Feature Overview

### What it does
- **Multi-Stage Builds**: Separates package installation (build stage) from runtime (final stage)
- **Pixi Support**: New `pixi/v1` template using the Pixi package manager
- **Enhanced Micromamba**: New `micromamba/v2` template with multi-stage builds
- **Singularity Support**: Both templates support Docker and Singularity formats
- **Lock File Generation**: Produces reproducible builds with `pixi.lock` or `environment.lock`

### How it works

1. User submits container request with `buildTemplate` field
2. Wave routes to appropriate template based on value (`pixi/v1` or `micromamba/v2`)
3. Multi-stage build executes:
   - **Stage 1 (build)**: Package manager installs dependencies
   - **Stage 2 (final)**: Only the conda environment is copied to minimal base image
4. Final image contains runtime only, no build tools

## Decision Drivers

1. **Image Size**: Need to reduce container image sizes for faster pulls and reduced storage
2. **Modern Tooling**: User demand for Pixi support (faster resolution, better UX)
3. **Reproducibility**: Lock files ensure exact package versions across builds
4. **Security**: Fewer binaries in final image reduces attack surface
5. **Backward Compatibility**: Existing workflows must continue working unchanged

## Technical Decisions

### 1. Template Selection via API Field

**Decision:** Added `buildTemplate` field to `SubmitContainerTokenRequest`

| Value | Template | Package Manager |
|-------|----------|-----------------|
| `null` (default) | micromamba v1 | micromamba (single-stage) |
| `conda-pixi/v1` | pixi v1 | pixi (multi-stage) |
| `conda-micromamba/v2` | micromamba v2 | micromamba (multi-stage) |

**Rationale:**
- Explicit selection avoids ambiguity
- Backward compatible (null = existing behavior)
- Easy to add new templates in future

### 2. Pixi as First-Class Package Manager

**Decision:** Added `PixiOpts` configuration class alongside existing `CondaOpts`

| Option | Default | Description |
|--------|---------|-------------|
| `pixiImage` | `ghcr.io/prefix-dev/pixi:0.59.0-noble` | Build stage image |
| `baseImage` | `ubuntu:24.04` | Final stage base |
| `basePackages` | `conda-forge::procps-ng` | Additional runtime packages |
| `commands` | (empty) | Custom commands |

**Rationale:**
- Pixi has different defaults and behavior than Micromamba
- Separate configuration prevents option confusion
- Allows Pixi-specific features (e.g., shell-hook activation)

### 3. Multi-Stage Build Architecture

**Decision:** Two-stage builds for both `pixi/v1` and `micromamba/v2`

| Stage | Purpose | Contents |
|-------|---------|----------|
| Build | Package installation | Full package manager, build tools, cache |
| Final | Runtime | Conda environment only, shell activation script |

**Rationale:**
- Significant size reduction (build tools excluded)
- Cleaner separation of concerns
- Industry standard practice for production containers

### 4. Environment Activation Strategy

**Decision:** Generate shell-hook script for automatic environment activation

- Pixi: `/shell-hook.sh` generated via `pixi shell-hook`
- Micromamba: Standard `/opt/conda` activation

**Rationale:**
- Ensures environment is active on container start
- No package manager binary needed at runtime
- Works with both interactive and non-interactive shells

### 5. Singularity Multi-Stage Support

**Decision:** Implemented multi-stage Singularity definitions for both templates

**Rationale:**
- HPC environments commonly use Singularity
- Feature parity between Docker and Singularity
- Uses same build logic, different definition syntax

## API Changes

### New Request Fields

| Field | Type | Location | Description |
|-------|------|----------|-------------|
| `buildTemplate` | String | `SubmitContainerTokenRequest` | Template selector |
| `pixiOpts` | Object | `PackagesSpec` | Pixi configuration |

### PackagesSpec Options Support

| Template | `condaOpts` | `pixiOpts` |
|----------|-------------|------------|
| Default (v1) | Yes | No |
| `conda-micromamba/v2` | Yes | No |
| `conda-pixi/v1` | No | Yes |

## Template Comparison

| Aspect | Default (v1) | `conda-micromamba/v2` | `conda-pixi/v1` |
|--------|--------------|----------------------|-----------------|
| Build stages | 1 | 2 | 2 |
| Package manager in final | Yes | No | No |
| Lock file | No | Yes | Yes |
| Base image configurable | Yes | Yes | Yes |
| Singularity support | Yes | Yes | Yes |
| Relative image size | Large | Small | Small |

## Files Changed

| Category | Files |
|----------|-------|
| API Models | `SubmitContainerTokenRequest.java`, `PackagesSpec.java`, `PixiOpts.java` |
| TypeSpec | `ContainerRequest.tsp`, `PackagesSpec.tsp`, `PixiOpts.tsp` |
| Templates | `pixi-v1/*.txt`, `micromamba-v2/singularityfile-*.txt` |
| Helpers | `DockerHelper.java`, `ContainerHelper.groovy` |
| Controller | `ContainerController.groovy` |
| Tests | `*Test.groovy` for all modified components |
| Docs | `api.md` |

## Consequences

### Positive
- Smaller container images (30-50% reduction typical)
- Reproducible builds via lock files
- Modern Pixi package manager available
- Better security posture (fewer binaries)
- Full Singularity support for HPC users

### Negative
- Build time slightly increased (two stages)
- More template files to maintain
- Users must learn new `buildTemplate` API field

### Neutral
- Existing API behavior unchanged when `buildTemplate` not specified
- Both Docker and Singularity supported equally

## References

- [Pixi Documentation](https://pixi.sh/)
- [Micromamba Documentation](https://mamba.readthedocs.io/en/latest/user_guide/micromamba.html)
- [Docker Multi-Stage Builds](https://docs.docker.com/build/building/multi-stage/)

---

**Status:** Implemented
**Date:** 2025-12-03
**Authors:** Wave Team
**Branch:** build-template