# Unified Security Scanner

This directory contains a unified Docker image that handles both container image scanning and Nextflow plugin scanning using Trivy.

## Overview

a **single unified image** handles both scan types through an intelligent bash script (`scan.sh`) that determines the appropriate scan method based on the target.

## Components

### `Dockerfile`
Builds a unified scanner image based on `aquasec/trivy` with additional tools:
- **trivy** - Security vulnerability scanner
- **oras** - OCI Registry As Storage client (for plugin downloads)
- **unzip** - Plugin extraction
- **bash** - Script execution
- **scan.sh** - Unified scan orchestration script

### `scan.sh`
The main orchestration script that handles both scan types:

**Parameters:**
1. `scan_type` - Either "container" or "plugin"
2. `target` - Container image name or plugin identifier
3. `work_dir` - Working directory for output files
4. `platform` - Container platform (e.g., linux/amd64) - optional
5. `timeout` - Scan timeout in minutes (default: 15)
6. `severity` - Vulnerability severity levels (e.g., CRITICAL,HIGH) - optional
7. `scan_format` - Scan format type (default|spdx|cyclonedx) - optional

**Features:**
- Automatically detects scan type based on target
- Handles plugin download, extraction, and scanning
- Generates multiple output formats (JSON vulnerability report + SBOM)
- Proper error handling and logging

## Building the Image

```bash
cd scanner
make build
```

Or manually:
```bash
docker build \
  --build-arg version=0.57.1 \
  --build-arg oras_version=1.2.0 \
  -t wave-scanner:latest .
```

## Usage

### Container Image Scan
```bash
docker run --rm -v /tmp/scan:/scan wave-scanner:latest \
  container \
  alpine:latest \
  /scan \
  linux/amd64 \
  15 \
  "CRITICAL,HIGH" \
  default
```

### Plugin Scan
```bash
docker run --rm -v /tmp/scan:/scan wave-scanner:latest \
  plugin \
  ghcr.io/nextflow-io/plugins/nf-amazon:2.0.0 \
  /scan \
  "" \
  15 \
  "CRITICAL,HIGH" \
  default
```

## Wave Integration

Wave automatically passes the correct parameters based on the scan target. No changes are needed to Wave configuration beyond updating the image name:

```yaml
wave:
  scan:
    image:
      name: "your-registry/wave-scanner:latest"
    # The plugin.image.name is now deprecated and optional
```

## Output Files

The scanner generates these files in the work directory:
- `report.json` - Vulnerability scan results (JSON format)
- `spdx.json` - SBOM in SPDX format

## Migration from Separate Images

If you're migrating from separate container and plugin scan images:

1. Build and push the unified scanner image
2. Update `wave.scan.image.name` to point to the unified image
3. (Optional) Remove `wave.scan.plugin.image.name` configuration
4. The system will automatically use the unified image for both scan types

## Troubleshooting

- **Permission issues**: Ensure the scan work directory is writable
- **Plugin download failures**: Check network connectivity and registry authentication
- **Timeout errors**: Increase the timeout parameter for large images/plugins
- **Cache issues**: The trivy cache is mounted at `/root/.cache/` - ensure it's accessible

## Development

To test locally:
```bash
# Build the image
docker build -t wave-scanner:test .

# Test container scan
docker run --rm -v $(pwd)/test-output:/scan wave-scanner:test container alpine:latest /scan

# Test plugin scan
docker run --rm -v $(pwd)/test-output:/scan wave-scanner:test plugin nf-amazon /scan
```
