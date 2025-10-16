# Security Scanning for Nextflow Plugins in OCI Registries

## Summary

This document describes the implementation of security scanning for Nextflow plugins hosted in OCI registries. The unified scanner can now detect and scan both traditional container images and Nextflow plugin artifacts, providing comprehensive vulnerability analysis across the entire Wave ecosystem.

## Context

Nextflow plugins are distributed as OCI artifacts in container registries, similar to container images but with different content types. These plugins contain JAR files and dependencies that need security scanning to detect vulnerabilities, just like container images. However, the scanning approach differs since plugins are not container images but rather ZIP archives containing Java artifacts.

## Feature Overview

### What it does
- **Unified Scanning Infrastructure**: Single scanner implementation handles both container images and Nextflow plugins
- **OCI Registry Support**: Pulls plugin artifacts from any OCI-compliant registry using ORAS
- **Vulnerability Detection**: Scans plugin dependencies and JAR libraries for known CVEs using Trivy
- **Multiple Output Formats**: Generates reports in JSON, SPDX, and CycloneDX formats
- **Multi-Architecture Support**: Scanner image works on both AMD64 and ARM64 platforms

### How it works

**Detection Flow:**
1. Scan request comes in with target image name
2. System detects scan type based on image name pattern (`nextflow/plugin` → plugin scan)
3. Scanner executes appropriate workflow:

**For Plugins:**
- Downloads artifact using ORAS from OCI registry
- Extracts plugin ZIP to temporary filesystem
- Performs rootfs scan with Trivy (`trivy rootfs --scanners vuln`)
- Generates vulnerability reports

**For Containers:**
- Performs standard container image scan with Trivy (`trivy image`)
- Analyzes all layers for vulnerabilities
- Generates vulnerability reports

## Decision Drivers

1. **Unified Infrastructure**: Need to minimize operational complexity by using a single scanner for all artifact types
2. **Flexibility**: Must support scanning of both standard OCI images and non-image OCI artifacts (plugins)
3. **Consistency**: Same vulnerability database and reporting format across all scan types
4. **Performance**: Efficient caching and resource utilization
5. **Multi-Architecture**: Support for both AMD64 and ARM64 platforms

## Technical Decisions

### 1. Unified Scanner Script

**Decision:** Refactored `scanner/scan.sh` into a unified script with `--type` parameter
- `--type container` → Container image scan
- `--type plugin` → Nextflow plugin scan

**Rationale:**
- Single script reduces maintenance overhead
- Ensures consistent scanning behavior across types
- Simplifies testing and deployment
- Easier to add new scan types in the future

**Alternatives Considered:**
- Separate scanner images for each type (rejected: increases maintenance)
- Runtime auto-detection without explicit type (rejected: ambiguous for edge cases)

### 2. ORAS for Plugin Downloads

**Decision:** Integrated [ORAS](https://oras.land/) (OCI Registry As Storage) tool into scanner image

**Rationale:**
- Industry-standard tool for OCI artifact management
- Native support for non-container artifacts (plugins, charts, etc.)
- Simple CLI interface integrates easily with bash scripts
- Maintained by CNCF, ensuring long-term support

**Alternatives Considered:**
- Custom OCI client implementation (rejected: reinventing the wheel)
- Using docker/skopeo for artifact pull (rejected: designed for container images)

### 3. Filesystem Scanning for Plugins

**Decision:** Plugins scanned using Trivy's `rootfs` scanner mode with `--scanners vuln` flag

**Rationale:**
- Plugins are JAR/ZIP artifacts, not container images
- Filesystem scanning detects vulnerabilities in Java dependencies
- Uses same Trivy vulnerability database as container scans
- Provides consistent security posture across all artifacts

**Alternatives Considered:**
- Container image wrapping (rejected: unnecessary complexity)
- Custom vulnerability scanner (rejected: maintenance burden)

### 4. Explicit Cache Directory Configuration

**Decision:** Added `--cache-dir` option to configure Trivy's cache location explicitly

**Rationale:**
- Improves reproducibility across environments
- Allows mounting external cache volumes in production for persistence
- Uses constant `Trivy.CACHE_MOUNT_PATH` for consistency across codebase
- Better control over disk usage and cache lifecycle

**Alternatives Considered:**
- Environment variable only (rejected: less explicit)
- Hardcoded path (rejected: inflexible for different environments)

### 5. Multi-Architecture Scanner Image

**Decision:** Fixed Dockerfile to properly support both `amd64` and `arm64` architectures using Docker's `TARGETARCH` build arg

**Rationale:**
- Critical for ARM-based infrastructure (AWS Graviton, Apple Silicon)
- Single image tag works across all architectures
- Reduces image management complexity
- Improves deployment flexibility

**Alternatives Considered:**
- Separate images per architecture (rejected: complex tag management)
- AMD64 only (rejected: excludes ARM environments)

### 6. Pattern-Based Scan Type Detection

**Decision:** Detects plugin scans by checking if image name contains `nextflow/plugin`

**Rationale:**
- Simple and effective for current use case
- No additional registry roundtrips required
- Provides clear migration path to manifest media type detection
- Works immediately without registry changes

**Alternatives Considered:**
- Manifest media type inspection (planned for future: #919)
- User-provided scan type parameter (rejected: additional API complexity)

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Wave Application                          │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ ContainerScanServiceImpl                            │    │
│  │  • fromBuild()                                      │    │
│  │  • fromMirror()                                     │    │
│  │  • fromContainer()                                  │    │
│  └──────────────────┬──────────────────────────────────┘    │
│                     │                                        │
│                     ▼                                        │
│  ┌────────────────────────────────────────────────────┐    │
│  │ ScanStrategy                                        │    │
│  │  • buildScanCommand()                               │    │
│  │  • Detects: container vs plugin                     │    │
│  └──────────────────┬──────────────────────────────────┘    │
│                     │                                        │
│                     ▼                                        │
│  ┌────────────────────────────────────────────────────┐    │
│  │ KubeScanStrategy / DockerScanStrategy               │    │
│  │  • Launches scanner container                       │    │
│  └──────────────────┬──────────────────────────────────┘    │
└───────────────────────┼────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│              Scanner Container Image                         │
│              (public.cr.seqera.io/wave/scanner:v1)          │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ /usr/local/bin/scan.sh                              │    │
│  │                                                      │    │
│  │  if [type == container]:                            │    │
│  │    trivy image --platform $PLATFORM $TARGET         │    │
│  │                                                      │    │
│  │  if [type == plugin]:                               │    │
│  │    oras pull $TARGET                                │    │
│  │    unzip plugin.zip                                 │    │
│  │    trivy rootfs --scanners vuln /extracted          │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  Components:                                                 │
│  • Trivy 0.65.0 (vulnerability scanner)                     │
│  • ORAS 1.3.0 (OCI artifact client)                         │
│  • bash, unzip (utilities)                                  │
└─────────────────────────────────────────────────────────────┘
```

## Implementation Details

### Files Changed

**Scanner Implementation:**
- `scanner/scan.sh` - Unified bash script with plugin support and `--cache-dir` option
- `scanner/Dockerfile` - Multi-arch build with ORAS integration and `TARGETARCH` support
- `scanner/Makefile` - Build configuration for multi-platform images

**Application Code:**
- `ScanStrategy.groovy` - Added `buildScanCommand()` with scan type detection and `--cache-dir` parameter
- `KubeScanStrategy.groovy` - Simplified to use unified command builder
- `DockerScanStrategy.groovy` - Simplified to use unified command builder
- `Trivy.groovy` - Added `CACHE_MOUNT_PATH` constant

**Tests:**
- `ScanStrategyTest.groovy` - Updated command expectations with `--cache-dir`
- `BuildScanCommandTest.groovy` - Updated command expectations with `--cache-dir`

### Testing

All tests passing:
```bash
./gradlew test --tests 'io.seqera.wave.service.scan.*Test'
```

Test coverage includes:
- Container scans with/without platform and severity
- Plugin scans with/without severity
- Scan type auto-detection logic
- Timeout conversions and parameter handling
- Cache directory configuration

### Deployment

**Building the Scanner Image:**

```bash
cd scanner
make build  # Builds for linux/amd64 and linux/arm64
```

This publishes:
- `public.cr.seqera.io/wave/scanner:v1-0.65.0-oras-1.3.0`
- `public.cr.seqera.io/wave/scanner:v1` (latest)

**Configuration:**

The application automatically uses the scanner via existing configuration:
```yaml
wave:
  scan:
    image:
      name: public.cr.seqera.io/wave/scanner:v1
```

## Example Usage

**Container Scan:**
```bash
/usr/local/bin/scan.sh \
  --type container \
  --target ubuntu:latest \
  --work-dir /tmp/scan \
  --platform linux/amd64 \
  --timeout 15 \
  --cache-dir /root/.cache/
```

**Plugin Scan:**
```bash
/usr/local/bin/scan.sh \
  --type plugin \
  --target ghcr.io/nextflow-io/nextflow/plugins/nf-amazon:1.0.0 \
  --work-dir /tmp/scan \
  --timeout 15 \
  --cache-dir /root/.cache/
```

## Benefits

1. ✅ **Unified Infrastructure** - Single image handles all security scanning
2. ✅ **Simplified Maintenance** - One scanner to build, test, and deploy
3. ✅ **Consistent Security** - Same Trivy version and vulnerability database
4. ✅ **Better Performance** - Explicit cache configuration and reuse
5. ✅ **Multi-Architecture** - Works on AMD64 and ARM64 platforms
6. ✅ **Future-Ready** - Easy to extend for new artifact types

## Consequences

### Positive
- Reduced operational complexity with single scanner image
- Consistent vulnerability detection across all artifact types
- Better resource utilization through unified caching
- Multi-architecture support enables broader platform compatibility
- Clear path for future artifact type additions

### Negative
- Pattern-based detection less robust than media type inspection (mitigated: planned for #919)
- Additional runtime dependency (ORAS) increases image size by ~10MB
- Plugin extraction requires temporary disk space during scanning

### Neutral
- Scanner image size increased from ~300MB to ~310MB (ORAS addition)
- Slight increase in scan time for plugins due to extraction step (~2-3 seconds)

## References

- [ORAS Project](https://oras.land/)
- [Trivy Documentation](https://aquasecurity.github.io/trivy/)
- [OCI Distribution Spec](https://github.com/opencontainers/distribution-spec)
- [Nextflow Plugins Documentation](https://www.nextflow.io/docs/latest/plugins.html)
- Wave Issue #919: Manifest media type detection

---

**Status:** Implemented
**Date:** 2025-10-16
**Authors:** Wave Team
**Related PR:** #912
