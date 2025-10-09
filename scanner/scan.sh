#!/bin/bash
set -e

# Unified scan script for both container images and Nextflow plugins
# Usage: scan.sh <scan_type> <target> <work_dir> <platform> <timeout> <severity> <scan_format>
#
# Parameters:
#   scan_type: "container" or "plugin"
#   target: container image name or plugin identifier
#   work_dir: working directory for output files
#   platform: container platform (e.g., linux/amd64) - can be empty for plugins
#   timeout: scan timeout in minutes (default: 15)
#   severity: vulnerability severity levels (e.g., CRITICAL,HIGH) - optional
#   scan_format: scan format type (default|spdx|cyclonedx) - optional

SCAN_TYPE="${1:-container}"
TARGET="${2}"
WORK_DIR="${3:-/tmp/scan}"

# Parameters are now consistent for both scan types
# Platform is passed for both, but will be empty for plugins
PLATFORM="${4}"
TIMEOUT="${5:-15}"
SEVERITY="${6}"
SCAN_FORMAT="${7:-default}"

CACHE_DIR="${TRIVY_CACHE_DIR:-/root/.cache/}"

# Validate required parameters
if [ -z "$TARGET" ]; then
    echo "Error: Target (container image or plugin) is required"
    exit 1
fi

# Create work directory if it doesn't exist
mkdir -p "$WORK_DIR"

# Function to run trivy scan
run_trivy_scan() {
    local scan_command=$1
    local target_path=$2
    local output_format=$3
    local output_file=$4

    local cmd="trivy --quiet $scan_command"

    [ -n "$PLATFORM" ] && cmd="$cmd --platform $PLATFORM"

    cmd="$cmd --timeout ${TIMEOUT}m"
    cmd="$cmd --format $output_format"
    cmd="$cmd --output $output_file"
    cmd="$cmd --cache-dir $CACHE_DIR"

    # Only add severity for default format
    if [ "$output_format" = "json" ] && [ -n "$SEVERITY" ]; then
        cmd="$cmd --severity $SEVERITY"
    fi

    cmd="$cmd $target_path"

    echo "Running: $cmd"
    eval "$cmd"
}

# Function to scan container image
scan_container() {
    echo "Scanning container image: $TARGET"

    # Determine output formats based on scan_format
    case "$SCAN_FORMAT" in
        default)
            run_trivy_scan "image" "$TARGET" "json" "$WORK_DIR/report.json"
            run_trivy_scan "image" "$TARGET" "spdx-json" "$WORK_DIR/spdx.json"
            ;;
        spdx)
            run_trivy_scan "image" "$TARGET" "spdx-json" "$WORK_DIR/spdx.json"
            ;;
        cyclonedx)
            run_trivy_scan "image" "$TARGET" "cyclonedx" "$WORK_DIR/cyclonedx.json"
            ;;
        *)
            echo "Error: Unknown scan format: $SCAN_FORMAT"
            exit 1
            ;;
    esac

    echo "Container scan completed successfully"
}

# Function to scan Nextflow plugin
scan_plugin() {
    echo "Scanning Nextflow plugin: $TARGET"

    local PLUGIN_DIR="$WORK_DIR/plugin"
    local FS_DIR="$WORK_DIR/fs"

    mkdir -p "$PLUGIN_DIR" "$FS_DIR"

    # Download plugin using oras
    echo "Downloading plugin with oras..."
    oras pull "$TARGET" -o "$PLUGIN_DIR"

    # Extract plugin zip file
    echo "Extracting plugin..."
    unzip -u "$PLUGIN_DIR"/*.zip -d "$FS_DIR"

    # Scan the extracted filesystem
    echo "Scanning plugin filesystem..."

    case "$SCAN_FORMAT" in
        default)
            run_trivy_scan "rootfs --scanners vuln" "$FS_DIR" "json" "$WORK_DIR/report.json"
            run_trivy_scan "rootfs --scanners vuln" "$FS_DIR" "spdx-json" "$WORK_DIR/spdx.json"
            ;;
        spdx)
            run_trivy_scan "rootfs --scanners vuln" "$FS_DIR" "spdx-json" "$WORK_DIR/spdx.json"
            ;;
        cyclonedx)
            run_trivy_scan "rootfs --scanners vuln" "$FS_DIR" "cyclonedx" "$WORK_DIR/cyclonedx.json"
            ;;
        *)
            echo "Error: Unknown scan format: $SCAN_FORMAT"
            exit 1
            ;;
    esac

    echo "Plugin scan completed successfully"
}

# Main execution
echo "========================================="
echo "Unified Scanner"
echo "========================================="
echo "Scan Type: $SCAN_TYPE"
echo "Target: $TARGET"
echo "Work Dir: $WORK_DIR"
echo "Platform: ${PLATFORM:-<not specified>}"
echo "Timeout: ${TIMEOUT}m"
echo "Severity: ${SEVERITY:-<not specified>}"
echo "Format: $SCAN_FORMAT"
echo "Cache Dir: $CACHE_DIR"
echo "========================================="

case "$SCAN_TYPE" in
    container)
        scan_container
        ;;
    plugin)
        scan_plugin
        ;;
    *)
        echo "Error: Unknown scan type: $SCAN_TYPE. Must be 'container' or 'plugin'"
        exit 1
        ;;
esac

echo "Scan completed successfully!"
