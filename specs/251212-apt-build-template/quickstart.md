# Quickstart: APT Build Template Implementation

**Feature**: 251212-apt-build-template
**Date**: 2025-12-12

## Prerequisites

- Java 21+ installed
- Gradle wrapper available (`./gradlew`)
- Wave development environment configured (see CLAUDE.md)

## Implementation Order

Follow this sequence to implement the APT build template:

### Step 1: Add AptOpts Configuration Class

**File**: `wave-api/src/main/java/io/seqera/wave/config/AptOpts.java`

```java
package io.seqera.wave.config;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AptOpts {
    public static final String DEFAULT_BASE_IMAGE = "ubuntu:24.04";
    public static final String DEFAULT_BASE_PACKAGES = "ca-certificates";

    public String baseImage;
    public String basePackages;
    public List<String> commands;

    public AptOpts() {
        this(Map.of());
    }

    public AptOpts(Map<String,?> opts) {
        this.baseImage = opts.containsKey("baseImage")
            ? opts.get("baseImage").toString()
            : DEFAULT_BASE_IMAGE;
        this.basePackages = opts.containsKey("basePackages")
            ? (String)opts.get("basePackages")
            : DEFAULT_BASE_PACKAGES;
        this.commands = opts.containsKey("commands")
            ? (List<String>)opts.get("commands")
            : null;
    }

    // Add fluent setters, equals, hashCode, toString
}
```

### Step 2: Update PackagesSpec

**File**: `wave-api/src/main/java/io/seqera/wave/api/PackagesSpec.java`

Add to enum:
```java
public enum Type { CONDA, CRAN, APT }
```

Add field:
```java
public AptOpts aptOpts;
```

Add fluent setter:
```java
public PackagesSpec withAptOpts(AptOpts opts) {
    this.aptOpts = opts;
    return this;
}
```

### Step 3: Update BuildTemplate

**File**: `wave-api/src/main/java/io/seqera/wave/api/BuildTemplate.java`

Add constant:
```java
public static final String APT_DEBIAN_V1 = "apt/debian:v1";
```

### Step 4: Create Template Files

**Directory**: `src/main/resources/templates/apt-debian-v1/`

Create 2 template files (APT has no environment file concept, only package list):
- `dockerfile-apt-packages.txt`
- `singularityfile-apt-packages.txt`

### Step 5: Create AptHelper

**File**: `src/main/groovy/io/seqera/wave/util/AptHelper.groovy`

Follow CranHelper pattern for structure.

### Step 6: Update ContainerHelper

**File**: `src/main/groovy/io/seqera/wave/util/ContainerHelper.groovy`

Add dispatch logic in `containerFileFromRequest()`.

### Step 7: Write Tests

**File**: `src/test/groovy/io/seqera/wave/util/AptHelperTest.groovy`

### Step 8: Update Documentation

**File**: `docs/api.md`

Add APT build template documentation:
- Add `APT` to the `type` field description
- Add `aptOpts` schema to request parameters
- Add `apt/debian:v1` to `buildTemplate` supported values
- Add example curl command for APT builds

## Quick Verification

After implementation, verify with:

```bash
# Build the project
./gradlew assemble

# Run tests
./gradlew test --tests 'AptHelperTest'

# Run all tests
./gradlew test
```

## Sample Test Request

```bash
curl -X POST http://localhost:9090/container-token \
  -H "Content-Type: application/json" \
  -d '{
    "buildTemplate": "apt/debian:v1",
    "packages": {
      "type": "APT",
      "entries": ["curl", "wget", "git"]
    }
  }'
```

## Key Files Reference

| File | Purpose |
|------|---------|
| `wave-api/.../config/AptOpts.java` | APT build configuration |
| `wave-api/.../api/PackagesSpec.java` | Add APT type and aptOpts |
| `wave-api/.../api/BuildTemplate.java` | Add APT_DEBIAN_V1 constant |
| `src/.../util/AptHelper.groovy` | Template rendering logic |
| `src/.../util/ContainerHelper.groovy` | Add dispatch logic |
| `src/.../resources/templates/apt-debian-v1/` | Template files (2 files) |
| `src/test/.../util/AptHelperTest.groovy` | Unit tests |
| `docs/api.md` | API documentation |

## Common Issues

### Import Errors
Ensure `AptOpts` import is added to `PackagesSpec.java`:
```java
import io.seqera.wave.config.AptOpts;
```

### Template Not Found
Verify template files are in correct location under `src/main/resources/templates/apt-debian-v1/`

### Build Failures
Check that `DEBIAN_FRONTEND=noninteractive` is set to prevent APT prompts.
