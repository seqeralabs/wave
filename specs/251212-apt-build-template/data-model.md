# Data Model: APT Build Template

**Feature**: 251212-apt-build-template
**Date**: 2025-12-12

## Entity Overview

This feature extends existing entities rather than creating new persistence structures. No database schema changes required.

## Modified Entities

### 1. PackagesSpec.Type (Enumeration)

**Location**: `wave-api/src/main/java/io/seqera/wave/api/PackagesSpec.java`

**Current Values**:
- `CONDA`
- `CRAN`

**New Value**:
- `APT` - Debian/Ubuntu APT package manager

### 2. PackagesSpec (API Model)

**Location**: `wave-api/src/main/java/io/seqera/wave/api/PackagesSpec.java`

**Existing Fields** (unchanged):
| Field | Type | Description |
|-------|------|-------------|
| `type` | `Type` | Package manager type |
| `environment` | `String` | Base64-encoded environment file |
| `entries` | `List<String>` | List of package names |
| `channels` | `List<String>` | Package channels/repositories |
| `condaOpts` | `CondaOpts` | Conda-specific options |
| `pixiOpts` | `PixiOpts` | Pixi-specific options |
| `cranOpts` | `CranOpts` | CRAN-specific options |

**New Field**:
| Field | Type | Description |
|-------|------|-------------|
| `aptOpts` | `AptOpts` | APT-specific build options |

**Validation Rules**:
- When `type == APT`:
  - Either `entries` OR `environment` must be provided (not both)
  - `environment` is parsed as newline-separated package names (comments with `#` and empty lines ignored)
  - `channels` field is ignored (APT uses default repos)
  - `aptOpts` is optional (defaults applied if null)

### 3. BuildTemplate (Constants)

**Location**: `wave-api/src/main/java/io/seqera/wave/api/BuildTemplate.java`

**Existing Constants**:
- `CONDA_PIXI_V1 = "conda/pixi:v1"`
- `CONDA_MICROMAMBA_V1 = "conda/micromamba:v1"`
- `CONDA_MICROMAMBA_V2 = "conda/micromamba:v2"`
- `CRAN_INSTALLR_V1 = "cran/installr:v1"`

**New Constant**:
- `APT_DEBIAN_V1 = "apt/debian:v1"`

## New Entities

### 4. AptOpts (Configuration Model)

**Location**: `wave-api/src/main/java/io/seqera/wave/config/AptOpts.java`

**Purpose**: Configuration options for APT-based container builds

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `baseImage` | `String` | `ubuntu:24.04` | Base Docker image for builds |
| `basePackages` | `String` | `ca-certificates` | Packages always installed before user packages |
| `commands` | `List<String>` | `null` | Additional shell commands to run after installation |

**Validation Rules**:
- `baseImage` must be non-empty if provided
- `basePackages` can be empty string to skip base packages
- `commands` entries must be valid shell commands

**Methods**:
- `AptOpts()` - Default constructor with default values
- `AptOpts(Map<String,?> opts)` - Constructor from map (for JSON deserialization)
- `withBaseImage(String)` - Fluent setter
- `withBasePackages(String)` - Fluent setter
- `withCommands(List<String>)` - Fluent setter
- `equals()`, `hashCode()`, `toString()` - Standard object methods

## Entity Relationships

```text
SubmitContainerTokenRequest
├── buildTemplate: String ("apt/debian:v1")
└── packages: PackagesSpec
    ├── type: Type.APT
    ├── entries: List<String>         # Option 1: package list
    ├── environment: String           # Option 2: base64-encoded newline-separated packages
    └── aptOpts: AptOpts
        ├── baseImage: String
        ├── basePackages: String
        └── commands: List<String>
```

## State Transitions

No state machines introduced. Build operations follow existing Wave build lifecycle:

```text
Request → Validate → Generate Container File → Queue Build → Build → Complete/Fail
```

## Data Volume Considerations

- No new persistence requirements
- Existing `WaveBuildRecord` stores `buildTemplate` field (already exists)
- No additional storage for APT-specific data

## Backward Compatibility

- All changes are additive
- Existing API requests without `type: APT` unaffected
- Existing build templates continue to work unchanged
- No migration required
