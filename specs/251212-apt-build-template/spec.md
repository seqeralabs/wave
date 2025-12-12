# Feature Specification: APT Build Template

**Feature Branch**: `251212-apt-build-template`
**Created**: 2025-12-12
**Status**: Draft
**Input**: User description: "Add a build template based on apt package manager as alternative to conda. It should be supported by both Docker and Singularity builds. Selection should be made by using the buildTemplate option."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Build Container with APT Packages via Package List (Priority: P1)

A bioinformatics user needs to create a container with system-level tools available through Debian/Ubuntu APT repositories (e.g., samtools, bedtools, curl, git). They want to specify a list of APT packages directly in their Wave API request and have Wave build a container with those packages installed.

**Why this priority**: This is the core functionality - allowing users to specify APT packages directly. Without this, the feature has no value.

**Independent Test**: Can be fully tested by submitting a container token request with `buildTemplate: "apt/debian:v1"` and a list of package names, then verifying the resulting container has those packages installed and functional.

**Acceptance Scenarios**:

1. **Given** a user submits a container token request with `buildTemplate: "apt/debian:v1"` and packages `["curl", "wget", "git"]`, **When** Wave processes the request, **Then** a container is built with curl, wget, and git installed and accessible from the command line.

2. **Given** a user submits a container token request with APT packages and a Singularity format flag, **When** Wave processes the request, **Then** a Singularity container is built with the specified packages installed.

3. **Given** a user specifies an APT package that doesn't exist in the repositories, **When** Wave attempts to build the container, **Then** the build fails with a clear error message indicating which package was not found.

---

### User Story 2 - Build Container with APT Packages via Requirements File (Priority: P2)

A user wants to define their APT package dependencies in a file (similar to conda's environment.yml) and have Wave build a container from that file specification. This allows version control of dependencies and easier sharing of environment definitions.

**Why this priority**: File-based specification is important for reproducibility and CI/CD workflows, but users can achieve basic functionality with package lists first.

**Independent Test**: Can be tested by submitting a request with a base64-encoded requirements file containing APT package names (one per line), then verifying the container has all listed packages installed.

**Acceptance Scenarios**:

1. **Given** a user submits a container token request with `buildTemplate: "apt/debian:v1"` and an `environment` field containing a base64-encoded file with package names, **When** Wave processes the request, **Then** a container is built with all packages from the file installed.

2. **Given** a requirements file contains comments (lines starting with #) and empty lines, **When** Wave parses the file, **Then** comments and empty lines are ignored and only valid package names are processed.

---

### User Story 3 - Customize APT Build with Base Image and Additional Commands (Priority: P3)

An advanced user needs to customize their APT-based container by specifying a different base image (e.g., Ubuntu 22.04 instead of default Debian) and running additional commands after package installation (e.g., downloading additional data, setting environment variables).

**Why this priority**: Customization extends the feature's flexibility but is not required for basic functionality.

**Independent Test**: Can be tested by submitting a request with custom `aptOpts` including a different base image and post-install commands, then verifying the container uses the specified image and executed the custom commands.

**Acceptance Scenarios**:

1. **Given** a user specifies `aptOpts.baseImage: "ubuntu:22.04"` in their request, **When** Wave builds the container, **Then** the resulting container is based on Ubuntu 22.04 instead of the default Debian image.

2. **Given** a user specifies `aptOpts.commands: ["echo 'setup complete' > /setup.log"]`, **When** Wave builds the container, **Then** the custom command is executed and `/setup.log` exists in the final container.

3. **Given** a user specifies `aptOpts.basePackages: ["ca-certificates", "locales"]`, **When** Wave builds the container, **Then** these packages are installed in addition to the user-specified packages.

---

### Edge Cases

- What happens when the user specifies conflicting packages (packages that cannot be installed together)?
  - The build should fail with the APT resolver error message.

- What happens when the user specifies a package with version constraints (e.g., `nginx=1.18.0-0ubuntu1`)?
  - Version-pinned packages should be supported using standard APT version syntax.

- What happens when the base image doesn't have APT available (e.g., Alpine Linux)?
  - The build should fail with a clear error indicating APT is not available in the base image.

- What happens when network issues prevent package download during build?
  - Standard Docker/Singularity build timeout and retry behavior applies; build fails with network error.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support a new build template identifier `apt/debian:v1` selectable via the `buildTemplate` request parameter.

- **FR-002**: System MUST generate valid Dockerfile content when APT template is selected for Docker builds.

- **FR-003**: System MUST generate valid Singularity definition file content when APT template is selected for Singularity builds.

- **FR-004**: System MUST support specifying APT packages as a list of package names in the `packages.entries` field.

- **FR-005**: System MUST support specifying APT packages via a file (base64-encoded in `packages.environment` field) with one package name per line.

- **FR-006**: System MUST support APT package version pinning using standard APT syntax (e.g., `package=version`).

- **FR-007**: System MUST support an `aptOpts` configuration object in `PackagesSpec` with the following options:
  - `baseImage`: Base Docker image to use (default: standard Debian-based image)
  - `basePackages`: List of packages to always install (e.g., ca-certificates)
  - `commands`: List of additional shell commands to run after package installation

- **FR-008**: System MUST run `apt-get update` before installing packages to ensure package lists are current.

- **FR-009**: System MUST clean up APT cache after installation to minimize final image size (`apt-get clean`, remove `/var/lib/apt/lists/*`).

- **FR-010**: System MUST set `DEBIAN_FRONTEND=noninteractive` during package installation to prevent interactive prompts.

- **FR-011**: System MUST use `--no-install-recommends` flag by default to produce minimal container images with explicit dependencies only.

- **FR-012**: System MUST add a new package type `APT` to the `PackagesSpec.Type` enumeration.

- **FR-013**: System MUST log the build template used for APT builds in the build record for traceability.

### Key Entities

- **BuildTemplate**: Extended to include the `apt/debian:v1` constant for APT-based builds.

- **PackagesSpec.Type**: Extended to include `APT` as a valid package type alongside `CONDA` and `CRAN`.

- **AptOpts**: New configuration entity for APT-specific build options (baseImage, basePackages, commands).

- **Requirements File**: Plain text file with one APT package name per line, supporting comments with `#` prefix.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can successfully build containers with APT packages using the same request flow as existing Conda/CRAN templates.

- **SC-002**: Both Docker and Singularity container formats are supported with identical package specifications.

- **SC-003**: Container builds complete successfully for standard Debian/Ubuntu APT packages available in default repositories.

- **SC-004**: Built containers have all specified packages installed and functional (verifiable via `dpkg -l` or direct command execution).

- **SC-005**: Final container images follow size optimization best practices (no APT cache, no package lists retained).

- **SC-006**: Build failures due to invalid packages produce clear, actionable error messages identifying the problematic package.

## Clarifications

### Session 2025-12-12

- Q: What should be the default base image for APT builds? → A: `ubuntu:24.04` (Latest Ubuntu LTS)
- Q: How should APT recommended packages be handled? → A: Default to `--no-install-recommends` for minimal images

## Assumptions

- The default base image is `ubuntu:24.04` (Ubuntu 24.04 LTS), providing long-term support, broad package availability, and security updates through 2029.
- Only official Debian/Ubuntu repositories are supported in v1; custom repository support may be added in future versions.
- Package names follow standard APT naming conventions without architecture suffixes (architecture determined by build platform).
- The requirements file format is intentionally simple (one package per line) to minimize parsing complexity and user errors.
