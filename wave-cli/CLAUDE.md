# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Wave CLI is a command-line tool for the Wave container provisioning service. It allows users to:
- Build container images on-demand from Dockerfiles or Conda/CRAN packages
- Augment existing containers with additional layers
- Build containers for specific platforms (linux/amd64, linux/arm64)
- Push containers to registries and enable Singularity format
- Mirror containers between registries
- Scan containers for security vulnerabilities

The CLI is built using Java 17 (with Java 21 toolchain) and compiles to a native binary using GraalVM.

## Architecture

### Core Components

**App.java** (`io.seqera.wave.cli.App`)
- Main entry point implementing `Runnable`
- Uses Picocli for CLI argument parsing with extensive `@Option` annotations
- Orchestrates the entire request lifecycle: validation → request creation → submission → response handling
- Handles multiple input modes: containerfile, image, conda packages, CRAN packages, layers
- Main flow: `main()` → `validateArgs()` → `run()` → `createRequest()` → `client().submit()`

**Client.java** (`io.seqera.wave.cli.Client`)
- HTTP client wrapper using Java 11+ HttpClient
- Implements retry logic with Failsafe library for resilient API calls
- Key methods:
  - `submit()` - Submit container build requests
  - `inspect()` - Inspect container metadata
  - `awaitCompletion()` - Poll for build completion status
  - `serviceInfo()` - Get Wave service version info

**Request/Response Flow**
1. User provides CLI options (image, containerfile, packages, etc.)
2. `App` validates arguments and prepares context (build context, layers, config)
3. Creates `SubmitContainerTokenRequest` with all specifications
4. `Client` submits to Wave API endpoint
5. Returns `SubmitContainerTokenResponse` with container image URL
6. Optional: `--await` flag polls until build completes

### Key Packages

- **cli/** - Main application logic and CLI interface
- **cli/model/** - Extended models wrapping Wave API types (e.g., `ContainerInspectResponseEx`)
- **cli/util/** - Utilities (BuildInfo, YamlHelper, Checkers, DurationConverter)
- **cli/json/** - JSON serialization using Moshi
- **cli/exception/** - Custom exceptions (IllegalCliArgumentException, BadClientResponseException, etc.)
- **cli/config/** - Configuration objects (RetryOpts, CondaOpts, CranOpts)

### GraalVM Native Image

The project compiles to a native binary with specific configuration:
- Native image config files in `app/conf/` (reflect-config.json, jni-config.json, etc.)
- These are critical for reflection-based libraries (Moshi, Picocli)
- When adding new API model classes that use reflection, update `reflect-config.json`
- Use `--agentlib` mode (configured in build.gradle) to auto-generate configs during development

## Development Commands

### Build and Test
```bash
# Compile Java sources
./gradlew assemble

# Run all tests (Spock framework in Groovy)
./gradlew test

# Compile and run tests
./gradlew check

# Run a single test class
./gradlew test --tests AppTest

# Run a single test method
./gradlew test --tests "AppTest.should fail when specifying mirror registry and container file"
```

### Native Compilation
```bash
# Build native binary (requires GraalVM Java 21)
./gradlew app:nativeCompile

# Run the native binary
./app/build/native/nativeCompile/wave --version

# Build shadow JAR (fat JAR with all dependencies)
./gradlew shadowJar
```

### Running the Application
```bash
# Run via Gradle
./gradlew run --args="-i alpine"

# Run the shadow JAR
java -jar app/build/libs/wave.jar -i alpine

# Run native binary after compilation
./app/build/native/nativeCompile/wave -i alpine
```

### Dependency Management
```bash
# View runtime dependencies
./gradlew app:dependencies --configuration runtimeClasspath

# View compile dependencies
./gradlew app:dependencies --configuration compileClasspath
```

## Testing Guidelines

### Test Framework
- Uses **Spock Framework** (Groovy-based BDD testing)
- Test files in `app/src/test/groovy/` with `.groovy` extension
- Main test: `AppTest.groovy` covers CLI argument validation and request creation

### Test Structure
```groovy
def 'should describe what the test does'() {
    given:
    def app = new App()
    String[] args = ["--option", "value"]

    when:
    new CommandLine(app).parseArgs(args)
    app.validateArgs()

    then:
    // assertions or expected exceptions
}
```

### Key Testing Patterns
- Use `CommandLine.parseArgs()` to simulate CLI input
- Call `app.validateArgs()` to trigger validation logic
- Use `thrown()` to verify exceptions: `def e = thrown(IllegalCliArgumentException)`
- Mock-free approach: tests primarily verify argument parsing and validation logic

## Important Implementation Details

### Boolean Flags vs Options
- Boolean flags like `--mirror`, `--freeze`, `--singularity` do NOT take values
- Correct: `--mirror`
- Incorrect: `--mirror true` (causes UnmatchedArgumentException)

### Package Types
The CLI supports three package ecosystems (mutually exclusive):
- **Conda**: `--conda-package` or `--conda-file` with `CondaOpts`
- **CRAN**: `--cran-package` with `CranOpts`
- Each has base image and run command customization options

### Build Context and Layers
- Build context (`--context`) requires a containerfile (`-f`)
- Context and layers are packaged as gzip tar archives using `Packer` utility
- Max sizes enforced: 5MB for build context, 1MB per layer, 10MB total for layers
- `.dockerignore` support via `DockerIgnoreFilter`

### Output Formats
- Default: prints only the container image URL
- `--output json` or `--output yaml`: structured output
- `--await`: waits for build completion, returns status in output

### Wave API Integration
- Primary dependency: `io.seqera:wave-api` (currently 1.28.0)
- Provides request/response models: `SubmitContainerTokenRequest`, `ContainerInspectRequest`, etc.
- API endpoint configurable via `--wave-endpoint` (default: https://wave.seqera.io)

### Tower/Platform Integration
- Optional Tower token (`--tower-token`) for authenticated builds
- Required for `--build-repo` (persistent container storage)
- Tower endpoint defaults to Seqera Platform Cloud: https://api.cloud.seqera.io

## Code Modification Guidelines

### Adding New CLI Options
1. Add `@Option` field to `App.java`
2. Update `validateArgs()` with validation logic
3. Update `createRequest()` to pass option to Wave API
4. Add tests in `AppTest.groovy`
5. Update usage examples in `app/src/main/resources/io/seqera/wave/cli/usage-examples.txt`

### Adding New Wave API Models
1. Update `wave-api` dependency version in `app/build.gradle`
2. Add reflection config to `app/conf/reflect-config.json` for any classes using reflection
3. Create extended model in `cli/model/` if additional logic needed (see `ContainerInspectResponseEx`)

### Modifying Native Image Configuration
- Run application with `--agentlib:native-image-agent=config-merge-dir=app/conf/` to auto-generate configs
- Manually verify and commit updated config files in `app/conf/`
- Test native build after changes: `./gradlew app:nativeCompile`

## Common Patterns

### Error Handling
- Use `IllegalCliArgumentException` for validation errors (caught in main and printed to stderr)
- Use `BadClientResponseException` for API response errors
- Use `ClientConnectionException` for network/connection issues
- All exceptions exit with code 1

### Environment Variables
- `TOWER_ACCESS_TOKEN` - default for `--tower-token`
- `TOWER_API_ENDPOINT` - default for `--tower-endpoint`
- `TOWER_WORKSPACE_ID` - default for `--tower-workspace-id`
- `WAVE_ENDPOINT` - default for `--wave-endpoint`

### Duration Parsing
- Custom `DurationConverter` for Picocli
- Supports: `10m`, `2s`, `1h`, etc.
- Used by `--await` option (default: 15 minutes)


## Release Process

1. Update the `VERSION` file with a semantic version.
2. Update the README with the new version number.
3. Update the `changelog.txt file with changes against previous release. Use `git log --oneline v<PREVIOUS VERSION>..HEAD` to determine the changes to be added.
4. Commit VERSION and changelog.txt file adding the tag `[release]` in the commit comment first line.
5. Git push to upstream master branch.
