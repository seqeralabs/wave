#!/bin/bash
set -e

# Unified scan script for both container images and Nextflow plugins
# Usage: scan.sh --type <container|plugin> --target <target> [options]
#
# Required options:
#   --type        Scan type: "container" or "plugin" (default: container)
#   --target      Container image name or plugin identifier
#
# Optional:
#   --work-dir    Working directory for output files (default: /tmp/scan)
#   --platform    Container platform, e.g., linux/amd64 (container only)
#   --timeout     Scan timeout in minutes (default: 15)
#   --severity    Vulnerability severity levels, e.g., CRITICAL,HIGH
#   --format      Scan format: default|spdx|cyclonedx (default: default)
#   --cache-dir   Cache directory for Trivy (default: /root/.cache/)

# Set defaults
SCAN_TYPE="${SCAN_TYPE:-container}"
WORK_DIR="${WORK_DIR:-/tmp/scan}"
TIMEOUT="${TIMEOUT:-15}"
SCAN_FORMAT="${SCAN_FORMAT:-default}"
CACHE_DIR="${CACHE_DIR:-/root/.cache/}"

# Parse command-line options
while [[ $# -gt 0 ]]; do
    case $1 in
        --type|--target|--work-dir|--platform|--timeout|--severity|--format|--cache-dir)
            [[ -z "$2" || "$2" == --* ]] && { echo "Error: $1 requires a value"; exit 1; }
            case $1 in
                --type) SCAN_TYPE="$2";;
                --target) TARGET="$2";;
                --work-dir) WORK_DIR="$2";;
                --platform) PLATFORM="$2";;
                --timeout) TIMEOUT="$2";;
                --severity) SEVERITY="$2";;
                --format) SCAN_FORMAT="$2";;
                --cache-dir) CACHE_DIR="$2";;
            esac
            shift 2;;
        *) echo "Unknown option: $1"; exit 1;;
    esac
done

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

    if [ -n "$PLATFORM" ] && [ "$PLATFORM" != "none" ]; then
      cmd="$cmd --platform $PLATFORM"
    fi

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
cat <<EOF
=========================================
Unified Scanner
=========================================
Scan Type: $SCAN_TYPE
Target: $TARGET
Work Dir: $WORK_DIR
Platform: ${PLATFORM:-<not specified>}
Timeout: ${TIMEOUT}m
Severity: ${SEVERITY:-<not specified>}
Format: $SCAN_FORMAT
Cache Dir: $CACHE_DIR
=========================================
EOF

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
