# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Wave is a containers provisioning service that allows building container images on-demand and acts as a proxy for container registries. It's built with Java/Groovy using the Micronaut framework and follows a microservices architecture.

## Key Commands

### Development
- **Run development server**: `./run.sh` (runs with continuous compilation and file watching)
- **Build project**: `./gradlew assemble` or `make compile`
- **Run tests**: `./gradlew test` or `make check`
- **Run specific test**: `./gradlew test --tests 'TestClassName'`
- **Build container image**: `./gradlew jibDockerBuild` or `make image`
- **Generate code coverage**: `./gradlew jacocoTestReport` (runs automatically after tests)

### Environment Setup
Wave requires several environment variables for registry authentication:
- `DOCKER_USER`/`DOCKER_PAT` for Docker Hub
- `QUAY_USER`/`QUAY_PAT` for Quay.io
- `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` for AWS ECR
- `AZURECR_USER`/`AZURECR_PAT` for Azure Container Registry

## Architecture

### Core Services
- **ContainerBuildService**: Manages container image building (Docker/Kubernetes strategies)
- **ContainerMirrorService**: Handles container mirroring operations
- **ContainerScanService**: Security vulnerability scanning
- **RegistryProxyService**: Acts as proxy between clients and registries
- **BlobCacheService**: Caches container layers and artifacts
- **JobManager**: Handles async job processing and queuing

### Key Controllers
- **ContainerController**: Main API for container provisioning (`/container-token`)
- **BuildController**: Container build operations
- **ScanController**: Security scanning endpoints
- **RegistryProxyController**: Registry proxy functionality

### Storage & Persistence
- Uses PostgreSQL with Micronaut Data JDBC
- Redis for caching and distributed state
- Object storage (AWS S3) for blob/artifact storage
- Kubernetes for production container builds

### Configuration
- Main config: `src/main/resources/application.yml`
- Environment-specific configs in `src/main/resources/application-*.yml`
- Uses Micronaut's configuration system with property injection

## Technology Stack
- **Framework**: Micronaut 4.10.4 with Netty runtime
- **Language**: Groovy with Java 21+
- **Build Tool**: Gradle with custom conventions
- **Container**: JIB for multi-platform builds (AMD64/ARM64)
- **Database**: PostgreSQL with HikariCP connection pooling
- **Cache**: Redis with Jedis client
- **Testing**: Spock 2 framework
- **Metrics**: Micrometer with Prometheus
- **Security**: JWT authentication for Tower integration

## Important Notes
- The codebase uses custom Gradle conventions defined in `buildSrc/`
- Container images are built using Amazon Corretto 25 with jemalloc
- The service requires Kubernetes cluster for production builds
- Rate limiting is implemented using Spillway library
- All async operations use Reactor pattern with Micronaut Reactor

## Release Process

1. Update the `VERSION` file with a semantic version
2. Update the `changelog.txt file with changes against previous release
3. Commit VERSION and changelog.txt file adding the tag `[release]` in the commit comment first line.
4. Git push to upstream master branch.
