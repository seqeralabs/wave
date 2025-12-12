# Research: APT Build Template

**Feature**: 251212-apt-build-template
**Date**: 2025-12-12

## Executive Summary

Research confirms that adding APT build template support is straightforward by following established patterns. The existing template system provides clear extension points with minimal risk.

## Research Findings

### 1. Build Template Architecture

**Decision**: Follow existing CranHelper pattern for APT implementation

**Rationale**:
- CranHelper is the closest analogue (single-stage build, system package manager)
- Clear separation: API types in `wave-api/`, helpers in `src/main/groovy/io/seqera/wave/util/`
- Template files in `src/main/resources/templates/{template-name}/`
- TemplateRenderer provides `{{variable}}` substitution

**Alternatives Considered**:
- Multi-stage build like CondaHelper v2: Rejected - APT doesn't benefit from multi-stage (no separate package manager image)
- Pixi pattern: Rejected - designed for lock files and conda environments

### 2. PackagesSpec.Type Enumeration

**Decision**: Add `APT` as new enum value alongside `CONDA` and `CRAN`

**Rationale**:
- Follows established pattern for package manager types
- Enables type-specific validation and dispatch
- Located in `wave-api/src/main/java/io/seqera/wave/api/PackagesSpec.java`

**Alternatives Considered**:
- String-based type: Rejected - loses type safety, inconsistent with existing code

### 3. AptOpts Configuration Class

**Decision**: Create new `AptOpts.java` following `CranOpts`/`CondaOpts` pattern

**Rationale**:
- Consistent with existing opts classes
- Fields: `baseImage`, `basePackages`, `commands`
- Default baseImage: `ubuntu:24.04` (per clarification)
- Located in `wave-api/src/main/java/io/seqera/wave/config/AptOpts.java`

**Alternatives Considered**:
- Reuse CondaOpts: Rejected - different semantics (no mambaImage, different defaults)

### 4. Template Structure

**Decision**: Create 2 template files in `templates/apt-debian-v1/`

**Rationale**:
- Pattern: `{dockerfile|singularityfile}-apt-packages.txt`
- Both `entries` (list) and `environment` (newline-separated file) use the same template
- `environment` file is parsed into a package list before template rendering
- Both Docker and Singularity formats required per spec

**Template Variables**:
| Variable | Description | Example |
|----------|-------------|---------|
| `{{base_image}}` | Base Docker image | `ubuntu:24.04` |
| `{{base_packages}}` | System packages to pre-install | `ca-certificates` |
| `{{target}}` | Space-separated package list | `curl wget git` |

### 5. APT Best Practices

**Decision**: Apply container best practices in templates

**Rationale**:
- `DEBIAN_FRONTEND=noninteractive` prevents interactive prompts
- `--no-install-recommends` minimizes image size (per clarification)
- `apt-get clean && rm -rf /var/lib/apt/lists/*` removes cache
- Single `RUN` layer for install + cleanup reduces image layers

**Template Pattern**:
```dockerfile
FROM {{base_image}}
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
    && apt-get install -y --no-install-recommends {{base_packages}} {{target}} \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*
```

### 6. ContainerHelper Dispatch Logic

**Decision**: Add APT dispatch in `containerFileFromRequest()` method

**Rationale**:
- Existing dispatch at `ContainerHelper.groovy:59-78`
- Add condition: `if(req.buildTemplate == APT_DEBIAN_V1) return AptHelper.containerFile(spec, singularity)`
- No default template for APT (must be explicitly requested)

**Alternatives Considered**:
- Auto-detect APT type: Rejected - explicit template selection is clearer, avoids ambiguity

### 7. Requirements File Format

**Decision**: Simple plain text format (one package per line)

**Rationale**:
- Consistent with APT conventions (`apt install $(cat packages.txt)`)
- Comments with `#` prefix supported
- Empty lines ignored
- No version constraint syntax in file (use entries for pinning)

**Example**:
```text
# System utilities
curl
wget
git
# Build tools
build-essential
```

### 8. BuildTemplate Constant

**Decision**: Add `APT_DEBIAN_V1 = "apt/debian:v1"` constant

**Rationale**:
- Follows naming convention: `{manager}/{variant}:{version}`
- "debian" variant indicates Debian/Ubuntu APT (vs potential future alpine/apk)
- Version v1 allows future iteration

**Alternatives Considered**:
- `apt/ubuntu:v1`: Rejected - APT works on Debian family, not Ubuntu-specific
- `deb/apt:v1`: Rejected - inconsistent with existing naming

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Breaking existing templates | Low | High | Additive changes only, existing tests remain |
| APT package not found | Medium | Low | Clear error messages from APT propagated |
| Image size bloat | Medium | Medium | `--no-install-recommends` default |
| Version conflicts | Low | Low | Standard APT resolver handles this |

## Implementation Dependencies

1. No external library dependencies required
2. No database schema changes required
3. No configuration file changes required (optional defaults could be added later)

## Next Steps

1. Create `AptOpts.java` configuration class
2. Add `APT` to `PackagesSpec.Type` enum
3. Add `APT_DEBIAN_V1` constant to `BuildTemplate.java`
4. Create `AptHelper.groovy` with template rendering logic
5. Create 2 template files in `templates/apt-debian-v1/` (dockerfile + singularityfile)
6. Add dispatch logic to `ContainerHelper.groovy`
7. Write unit tests in `AptHelperTest.groovy`
8. Update `docs/api.md` with APT build template documentation
