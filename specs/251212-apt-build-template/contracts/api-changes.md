# API Contract Changes: APT Build Template

**Feature**: 251212-apt-build-template
**Date**: 2025-12-12

## Overview

This document describes the API changes required to support APT build templates. All changes are additive and backward-compatible.

## Endpoint: POST /container-token

**No changes to endpoint URL or method**. Changes are in request body schema only.

### Request Body Changes

#### New Enum Value: packages.type

```json
{
  "packages": {
    "type": "APT"  // NEW: alongside existing "CONDA", "CRAN"
  }
}
```

#### New Field: packages.aptOpts

```json
{
  "packages": {
    "type": "APT",
    "aptOpts": {
      "baseImage": "ubuntu:24.04",
      "basePackages": "ca-certificates",
      "commands": ["echo 'setup complete'"]
    }
  }
}
```

#### New Build Template Value

```json
{
  "buildTemplate": "apt/debian:v1"  // NEW template identifier
}
```

### Full Request Examples

#### Example 1: APT packages via entries list

```json
{
  "buildTemplate": "apt/debian:v1",
  "packages": {
    "type": "APT",
    "entries": [
      "curl",
      "wget",
      "git",
      "build-essential"
    ]
  }
}
```

#### Example 2: APT packages via environment file

```json
{
  "buildTemplate": "apt/debian:v1",
  "packages": {
    "type": "APT",
    "environment": "IyBTeXN0ZW0gdXRpbGl0aWVzCmN1cmwKd2dldApnaXQK"
  }
}
```

Note: `environment` is base64-encoded. Decoded content (newline-separated packages):
```text
# System utilities
curl
wget
git
```

#### Example 3: APT with custom options

```json
{
  "buildTemplate": "apt/debian:v1",
  "packages": {
    "type": "APT",
    "entries": ["nginx"],
    "aptOpts": {
      "baseImage": "ubuntu:22.04",
      "basePackages": "ca-certificates locales",
      "commands": [
        "locale-gen en_US.UTF-8",
        "update-locale LANG=en_US.UTF-8"
      ]
    }
  }
}
```

#### Example 4: APT with version pinning

```json
{
  "buildTemplate": "apt/debian:v1",
  "packages": {
    "type": "APT",
    "entries": [
      "nginx=1.18.0-0ubuntu1",
      "curl"
    ]
  }
}
```

#### Example 5: Singularity format

```json
{
  "buildTemplate": "apt/debian:v1",
  "format": "sif",
  "packages": {
    "type": "APT",
    "entries": ["samtools", "bcftools"]
  }
}
```

### Response

**No changes to response schema**. Existing `SubmitContainerTokenResponse` structure unchanged.

## Schema Definitions

### AptOpts Schema

```yaml
AptOpts:
  type: object
  properties:
    baseImage:
      type: string
      description: Base Docker image for APT builds
      default: "ubuntu:24.04"
      example: "ubuntu:22.04"
    basePackages:
      type: string
      description: Space-separated list of packages to always install
      default: "ca-certificates"
      example: "ca-certificates locales"
    commands:
      type: array
      items:
        type: string
      description: Additional shell commands to run after package installation
      example: ["locale-gen en_US.UTF-8"]
```

### PackagesSpec.Type Enum

```yaml
PackagesSpec.Type:
  type: string
  enum:
    - CONDA
    - CRAN
    - APT   # NEW
```

### BuildTemplate Values

```yaml
BuildTemplate:
  type: string
  enum:
    - "conda/pixi:v1"
    - "conda/micromamba:v1"
    - "conda/micromamba:v2"
    - "cran/installr:v1"
    - "apt/debian:v1"   # NEW
```

## Validation Rules

### Request Validation

| Condition | Error |
|-----------|-------|
| `type: APT` without `buildTemplate: apt/debian:v1` | `400 Bad Request: APT packages require buildTemplate 'apt/debian:v1'` |
| `buildTemplate: apt/debian:v1` without `type: APT` | `400 Bad Request: Build template 'apt/debian:v1' requires package type 'APT'` |
| Both `entries` and `environment` provided | `400 Bad Request: Cannot specify both entries and environment` |
| Neither `entries` nor `environment` provided | `400 Bad Request: Must specify either entries or environment` |
| Empty `entries` list | `400 Bad Request: Package entries cannot be empty` |

## Error Responses

### Build Failures

APT-related build failures return standard Wave build error format:

```json
{
  "status": "FAILED",
  "error": "E: Unable to locate package nonexistent-package",
  "buildId": "abc123"
}
```

## Backward Compatibility

| Aspect | Status |
|--------|--------|
| Existing CONDA requests | Unchanged |
| Existing CRAN requests | Unchanged |
| Existing build templates | Unchanged |
| Response schema | Unchanged |
| Authentication | Unchanged |

## Testing Considerations

### Contract Tests

1. Verify `type: APT` accepted in request
2. Verify `aptOpts` fields parsed correctly
3. Verify `buildTemplate: apt/debian:v1` accepted
4. Verify validation errors for invalid combinations
5. Verify Singularity format works with APT
