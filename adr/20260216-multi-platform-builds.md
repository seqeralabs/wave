# Multi-Platform Container Builds

## Overview

Wave currently builds container images for a single platform per request (e.g. `linux/amd64` or
`linux/arm64`). Clients that need both architectures must make two separate requests, resulting in
two independent image tags with no manifest list linking them.

This design adds a `multiPlatform` flag to the container request API. When enabled, Wave builds
a single OCI manifest list containing images for both `linux/amd64` and `linux/arm64`, using
native hardware for each architecture.

## Architecture

### Current state

Each build request carries a single `ContainerPlatform`. The K8s build Job runs
`buildctl-daemonless.sh` (an ephemeral, self-contained buildkitd) on a node matching the
requested architecture:

```
Request (platform=linux/amd64)
  -> K8s Job on amd64 node
  -> buildctl-daemonless.sh build --opt platform=linux/amd64
  -> push single-arch image
```

### Proposed state

For multi-platform builds, Wave uses the **`buildx` remote driver** to connect to **persistent
buildkitd daemons** running natively on both amd64 and arm64 nodes. Buildx handles platform
routing, parallel builds, manifest list assembly, and registry push — all in one command.

```
Request (multiPlatform=true)
  -> K8s Job (client-only, runs on any node)
  -> buildx create --driver remote (points to buildkitd-amd64 + buildkitd-arm64)
  -> buildx build --platform linux/amd64,linux/arm64
  -> buildx routes amd64 work to buildkitd-amd64, arm64 work to buildkitd-arm64
  -> buildx assembles manifest list and pushes
```

```
                                ┌──────────────────────────┐
                                │   K8s Job: buildx client │
                                │   --platform             │
                                │     linux/amd64,         │
                                │     linux/arm64          │
                                └─────┬──────────┬─────────┘
                                      │          │
                            ┌─────────┘          └──────────┐
                            ▼                               ▼
                   ┌─────────────────┐            ┌─────────────────┐
                   │  buildkitd-amd64│            │  buildkitd-arm64│
                   │  (Deployment)   │            │  (Deployment)   │
                   │  amd64 node     │            │  arm64 node     │
                   │  tcp://:1234    │            │  tcp://:1234    │
                   └────────┬────────┘            └────────┬────────┘
                            │                              │
                            ▼                              ▼
                     native amd64 build             native arm64 build
                            │                              │
                            └──────────┬───────────────────┘
                                       ▼
                              Registry: <tag>
                              (OCI manifest list with
                               amd64 + arm64 images)
```

### Why this approach

- **Native speed on both architectures.** No QEMU emulation. Conda package installs (the
  heaviest build step) run at full speed on native hardware.
- **Single build from Wave's perspective.** Buildx handles all multi-platform coordination.
  Wave launches one K8s Job and waits for it, same as today.
- **Persistent buildkitd with warm cache.** Buildkitd daemons keep layer caches across builds,
  avoiding repeated downloads and installs.
- **No Docker daemon required.** The `buildx` binary works standalone with the `remote` driver.
  The build Job pod only needs the `buildx` binary and TLS client certificates.

## API Changes

### SubmitContainerTokenRequest

New field in `wave-api`:

```java
/**
 * When true, build a multi-platform image (linux/amd64 + linux/arm64)
 * as a single OCI manifest list.
 */
public boolean multiPlatform;
```

With corresponding builder method:

```java
public SubmitContainerTokenRequest withMultiPlatform(boolean value) {
    this.multiPlatform = value;
    return this;
}
```

### Validation rules

In `ContainerController.handleRequest()`:

- `multiPlatform` requires `containerFile` or `packages` (build mode only, not plain container pull)
- `multiPlatform` is incompatible with `format: "sif"` (Singularity has no manifest list concept)
- When `multiPlatform: true`, the `containerPlatform` field is ignored — it is overridden to a
  fixed set of `linux/amd64,linux/arm64`

## Wave Code Changes

### BuildRequest

Add a `multiPlatform` field:

```groovy
class BuildRequest {
    // ... existing fields ...

    /** Whether this is a multi-platform build */
    final boolean multiPlatform

    // Update constructor to accept multiPlatform parameter
}
```

### ContainerHelper.makeContainerId

The `platform` parameter already contributes to the containerId hash. For multi-platform builds,
set the platform string to `"linux/amd64,linux/arm64"` so that multi-platform builds produce a
distinct containerId from single-platform builds of the same Dockerfile.

### ContainerController.makeBuildRequest

When `req.multiPlatform` is true:

```groovy
final platform = req.multiPlatform
    ? ContainerPlatform.of("amd64")  // nominal platform for hash; actual platforms in buildx cmd
    : ContainerPlatform.parseOrDefault(req.containerPlatform)
```

Pass `multiPlatform: true` through to the `BuildRequest` constructor.

### BuildStrategy

Add a method to generate the buildx launch command:

```groovy
protected List<String> multiPlatformLaunchCmd(BuildRequest req) {
    final result = new ArrayList(20)

    // Step 1: register the remote builders
    result << "buildx" << "create"
    result << "--name" << "wave"
    result << "--driver" << "remote"
    result << "--platform" << "linux/amd64"
    result << "--driver-opt" << "cacert=/buildkit-certs/ca.pem,cert=/buildkit-certs/cert.pem,key=/buildkit-certs/key.pem"
    result << buildConfig.buildkitdAmd64Addr

    result << "&&"

    result << "buildx" << "create"
    result << "--name" << "wave"
    result << "--append"
    result << "--driver" << "remote"
    result << "--platform" << "linux/arm64"
    result << "--driver-opt" << "cacert=/buildkit-certs/ca.pem,cert=/buildkit-certs/cert.pem,key=/buildkit-certs/key.pem"
    result << buildConfig.buildkitdArm64Addr

    result << "&&"

    // Step 2: build
    result << "buildx" << "build"
    result << "--builder" << "wave"
    result << "--platform" << "linux/amd64,linux/arm64"
    result << "--file" << "$req.workDir/Containerfile".toString()
    result << "--output" << outputOpts(req, buildConfig)
    result << "$req.workDir/context".toString()

    if (req.cacheRepository) {
        result << "--cache-to" << cacheExportOpts(req, buildConfig)
        result << "--cache-from" << cacheImportOpts(req, buildConfig)
    }

    return result
}
```

Note: the entire sequence runs as a shell command (`sh -c "buildx create ... && buildx create
--append ... && buildx build ..."`).

### KubeBuildStrategy

Override `build()` to branch on `multiPlatform`:

```groovy
@Override
void build(String jobName, BuildRequest req) {
    final configFile = req.configJson ? req.workDir.resolve('config.json') : null
    final timeout = req.maxDuration ?: buildConfig.defaultTimeout

    if (req.multiPlatform) {
        final buildCmd = multiPlatformLaunchCmd(req)
        final selector = getNoArchSelector(nodeSelectorMap)
        k8sService.launchMultiPlatformBuildJob(
            jobName, buildConfig.buildxImage, buildCmd,
            req.workDir, configFile, timeout, selector
        )
    } else {
        // Existing single-platform path — unchanged
        final buildImage = getBuildImage(req)
        final buildCmd = launchCmd(req)
        final selector = getSelectorLabel(req.platform, nodeSelectorMap)
        k8sService.launchBuildJob(
            jobName, buildImage, buildCmd,
            req.workDir, configFile, timeout, selector
        )
    }
}
```

### K8sServiceImpl

New method `launchMultiPlatformBuildJob()`. The Job spec differs from the existing build job:

| Aspect                | Single-platform (existing)              | Multi-platform (new)                     |
|-----------------------|-----------------------------------------|------------------------------------------|
| Container image       | `buildkit:v0.25.2-rootless`             | Custom image with `buildx` binary        |
| Entrypoint            | `buildctl-daemonless.sh`                | `sh -c`                                  |
| Args                  | `build --frontend dockerfile.v0 ...`    | `buildx create ... && buildx build ...`  |
| BUILDKITD_FLAGS env   | `--oci-worker-no-process-sandbox`       | Not needed (no local daemon)             |
| Security context      | AppArmor: Unconfined                    | Default (no special privileges)          |
| Node selector         | Platform-specific (amd64 or arm64)      | `noarch` (client runs anywhere)          |
| Extra volumes         | None                                    | buildkit-client-certs Secret             |

```groovy
V1Job launchMultiPlatformBuildJob(
    String name, String containerImage, List<String> args,
    Path workDir, Path creds, Duration timeout, Map<String,String> nodeSelector
) {
    final spec = multiPlatformBuildJobSpec(name, containerImage, args, workDir, creds, timeout, nodeSelector)
    return k8sClient
        .batchV1Api()
        .createNamespacedJob(namespace, spec)
        .execute()
}
```

The pod spec mounts:
- `build-data` volume (PVC) for Containerfile, conda.yml, context — same as today
- `buildkit-client-certs` Secret for mTLS to the buildkitd daemons
- Registry credentials at `/home/user/.docker/config.json` — same as today

### BuildConfig

New configuration properties:

```groovy
@Nullable
@Value('${wave.build.buildx-image}')
String buildxImage

@Nullable
@Value('${wave.build.buildkitd.amd64-addr}')
String buildkitdAmd64Addr

@Nullable
@Value('${wave.build.buildkitd.arm64-addr}')
String buildkitdArm64Addr
```

Example `application.yml`:

```yaml
wave:
  build:
    buildx-image: "public.cr.seqera.io/wave/buildx:v0.20.0"
    buildkitd:
      amd64-addr: "tcp://buildkitd-amd64.wave-build.svc:1234"
      arm64-addr: "tcp://buildkitd-arm64.wave-build.svc:1234"
```

## Kubernetes Infrastructure

### Prerequisites

- K8s cluster with **amd64 and arm64 node pools**
- Nodes labeled with `kubernetes.io/arch` (standard K8s label) and `service=wave-build`
  (existing Wave convention)
- Shared PVC `wave-build-fs` accessible from both node pools (existing)

### Component overview

```
Namespace: wave-build

Secrets:
  - buildkitd-daemon-certs    (CA + server cert/key for buildkitd)
  - buildkitd-client-certs    (CA + client cert/key for buildx)

ConfigMap:
  - buildkitd-config           (buildkitd.toml)

Deployments:
  - buildkitd-amd64            (1 replica on amd64 nodes)
  - buildkitd-arm64            (1 replica on arm64 nodes)

Services:
  - buildkitd-amd64            (ClusterIP, port 1234)
  - buildkitd-arm64            (ClusterIP, port 1234)
```

### TLS Certificate Generation

```bash
#!/bin/bash
# create-buildkitd-certs.sh
# Requires: mkcert (https://github.com/FiloSottile/mkcert)

set -euo pipefail

NAMESPACE="wave-build"
SAN_AMD64="buildkitd-amd64.${NAMESPACE}.svc"
SAN_ARM64="buildkitd-arm64.${NAMESPACE}.svc"

mkdir -p certs/{daemon,client}

# Install local CA
mkcert -install

# Daemon certificate (valid for both service DNS names)
mkcert \
  -cert-file certs/daemon/cert.pem \
  -key-file certs/daemon/key.pem \
  ${SAN_AMD64} ${SAN_ARM64} 127.0.0.1

# Client certificate
mkcert -client \
  -cert-file certs/client/cert.pem \
  -key-file certs/client/key.pem \
  ${SAN_AMD64} ${SAN_ARM64}

# Copy CA
cp "$(mkcert -CAROOT)/rootCA.pem" certs/ca.pem

# Create K8s secrets
kubectl -n ${NAMESPACE} create secret generic buildkitd-daemon-certs \
  --from-file=ca.pem=certs/ca.pem \
  --from-file=cert.pem=certs/daemon/cert.pem \
  --from-file=key.pem=certs/daemon/key.pem \
  --dry-run=client -o yaml > buildkitd-daemon-certs.yaml

kubectl -n ${NAMESPACE} create secret generic buildkitd-client-certs \
  --from-file=ca.pem=certs/ca.pem \
  --from-file=cert.pem=certs/client/cert.pem \
  --from-file=key.pem=certs/client/key.pem \
  --dry-run=client -o yaml > buildkitd-client-certs.yaml

echo "Apply with: kubectl apply -f buildkitd-daemon-certs.yaml -f buildkitd-client-certs.yaml"
```

### buildkitd.toml ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: buildkitd-config
  namespace: wave-build
data:
  buildkitd.toml: |
    [grpc]
      address = ["tcp://0.0.0.0:1234"]

    [grpc.tls]
      cert = "/certs/cert.pem"
      key = "/certs/key.pem"
      ca = "/certs/ca.pem"

    [worker.oci]
      enabled = true
      gc = true
      max-parallelism = 4

    [[worker.oci.gcpolicy]]
      keepDuration = "72h"
      maxUsedSpace = "50GB"

    [[worker.oci.gcpolicy]]
      all = true
      keepDuration = "168h"
      maxUsedSpace = "80GB"
```

### buildkitd Deployment (amd64)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: buildkitd-amd64
  namespace: wave-build
  labels:
    app: buildkitd
    arch: amd64
spec:
  replicas: 1
  selector:
    matchLabels:
      app: buildkitd
      arch: amd64
  template:
    metadata:
      labels:
        app: buildkitd
        arch: amd64
    spec:
      nodeSelector:
        kubernetes.io/arch: amd64
        service: wave-build
      serviceAccountName: wave-build-sa
      containers:
      - name: buildkitd
        image: public.cr.seqera.io/wave/buildkit:v0.25.2-rootless
        args:
        - --addr
        - unix:///run/user/1000/buildkit/buildkitd.sock
        - --addr
        - tcp://0.0.0.0:1234
        - --oci-worker-no-process-sandbox
        - --config
        - /etc/buildkit/buildkitd.toml
        ports:
        - containerPort: 1234
          name: grpc
        readinessProbe:
          exec:
            command: ["buildctl", "debug", "workers"]
          initialDelaySeconds: 5
          periodSeconds: 30
        livenessProbe:
          exec:
            command: ["buildctl", "debug", "workers"]
          initialDelaySeconds: 5
          periodSeconds: 30
        securityContext:
          seccompProfile:
            type: Unconfined
          appArmorProfile:
            type: Unconfined
          runAsUser: 1000
          runAsGroup: 1000
        resources:
          requests:
            cpu: "2"
            memory: 4Gi
          limits:
            cpu: "4"
            memory: 8Gi
        volumeMounts:
        - name: certs
          mountPath: /certs
          readOnly: true
        - name: config
          mountPath: /etc/buildkit
          readOnly: true
        - name: buildkitd-data
          mountPath: /home/user/.local/share/buildkit
        - name: build-data
          mountPath: /efs/wave/build
          readOnly: true
      volumes:
      - name: certs
        secret:
          secretName: buildkitd-daemon-certs
      - name: config
        configMap:
          name: buildkitd-config
      - name: buildkitd-data
        emptyDir:
          sizeLimit: 100Gi
      - name: build-data
        persistentVolumeClaim:
          claimName: wave-build-fs
```

### buildkitd Deployment (arm64)

Same as amd64 with these differences:

```yaml
metadata:
  name: buildkitd-arm64
  labels:
    arch: arm64
spec:
  selector:
    matchLabels:
      arch: arm64
  template:
    metadata:
      labels:
        arch: arm64
    spec:
      nodeSelector:
        kubernetes.io/arch: arm64
        service: wave-build
```

### Services

```yaml
apiVersion: v1
kind: Service
metadata:
  name: buildkitd-amd64
  namespace: wave-build
  labels:
    app: buildkitd
    arch: amd64
spec:
  type: ClusterIP
  ports:
  - port: 1234
    targetPort: 1234
    protocol: TCP
    name: grpc
  selector:
    app: buildkitd
    arch: amd64
---
apiVersion: v1
kind: Service
metadata:
  name: buildkitd-arm64
  namespace: wave-build
  labels:
    app: buildkitd
    arch: arm64
spec:
  type: ClusterIP
  ports:
  - port: 1234
    targetPort: 1234
    protocol: TCP
    name: grpc
  selector:
    app: buildkitd
    arch: arm64
```

## Build Flow (step by step)

### Single-platform (unchanged)

1. Client sends `POST /v1alpha2/container` with `containerPlatform: "linux/amd64"`
2. `ContainerController` creates `BuildRequest` with `platform=linux/amd64`, `multiPlatform=false`
3. `KubeBuildStrategy.build()` launches K8s Job with `buildctl-daemonless.sh` on amd64 node
4. Buildkit builds and pushes single-arch image
5. Wave retrieves digest and stores `BuildResult`

### Multi-platform (new)

1. Client sends `POST /v1alpha2/container` with `multiPlatform: true`
2. `ContainerController` validates (no singularity, requires build mode)
3. `ContainerController` creates `BuildRequest` with `multiPlatform=true`
4. `KubeBuildStrategy.build()` detects `multiPlatform` and launches a multi-platform K8s Job:
   - Container image: `buildx` image (with buildx binary)
   - Entrypoint: `sh -c`
   - Command:
     ```
     buildx create --name wave --driver remote \
       --platform linux/amd64 \
       --driver-opt cacert=...,cert=...,key=... \
       tcp://buildkitd-amd64.wave-build.svc:1234 \
     && buildx create --name wave --append --driver remote \
       --platform linux/arm64 \
       --driver-opt cacert=...,cert=...,key=... \
       tcp://buildkitd-arm64.wave-build.svc:1234 \
     && buildx build --builder wave \
       --platform linux/amd64,linux/arm64 \
       --file /path/to/Containerfile \
       --output type=image,name=<targetImage>,push=true \
       /path/to/context
     ```
   - Node selector: `noarch` (runs on any node)
   - Mounts: build-data PVC + client TLS certs Secret
5. Buildx connects to both persistent buildkitd daemons
6. Each buildkitd builds natively for its own architecture
7. Buildx assembles the OCI manifest list and pushes to registry
8. Wave job monitor detects completion, retrieves the manifest list digest
9. `BuildResult` stored with the manifest list digest

## Registry Credentials

The persistent buildkitd daemons need credentials to push images to the target registry.

### For POC

Mount the Wave build-data PVC (which contains per-build `config.json` files) into the buildkitd
pods as read-only. Buildx passes credentials from the client side: the build Job pod mounts
credentials at `/home/user/.docker/config.json` (same as today), and buildx forwards them to
the remote buildkitd via the build session.

Buildx's remote driver forwards the Docker auth configuration from the client to the remote
daemon during the build session. This means the client pod's `config.json` is used for
registry operations, and the daemon doesn't need static credentials.

### For production

Consider using:
- AWS IRSA on the buildkitd service account for ECR authentication
- K8s Secrets with registry credentials mounted into buildkitd pods
- A credential proxy that refreshes short-lived tokens

## Caching

### Build layer cache

The persistent buildkitd daemons retain their local layer cache in the `buildkitd-data`
emptyDir volume. This means repeated builds of similar Dockerfiles (same base image, similar
conda packages) benefit from cached layers without re-downloading or re-installing.

Note: `emptyDir` is lost on pod restart. For persistent cache across restarts, use a PVC
for the `buildkitd-data` volume instead.

### Registry-side cache

The existing `--export-cache` and `--import-cache` mechanism continues to work. Buildx
supports `--cache-to` and `--cache-from` with the same syntax:

```
--cache-to type=registry,ref=<cacheRepo>:<containerId>,mode=max
--cache-from type=registry,ref=<cacheRepo>:<containerId>
```

This provides cache persistence independent of buildkitd pod lifecycle.

## Buildx Container Image

A custom container image is needed for the multi-platform build Jobs:

```dockerfile
FROM docker/buildx-bin:0.20.0 AS buildx
FROM alpine:3.20
COPY --from=buildx /buildx /usr/local/bin/buildx
# buildx expects this directory
RUN mkdir -p /home/user/.docker/buildx && \
    adduser -D -u 1000 user
USER 1000
ENTRYPOINT ["sh", "-c"]
```

This image is minimal (Alpine + buildx binary), needs no Docker daemon, and runs as
non-root user 1000 (consistent with existing Wave build conventions).

Publish as: `public.cr.seqera.io/wave/buildx:v0.20.0`

## Configuration Summary

### New application.yml properties

```yaml
wave:
  build:
    # Existing properties (unchanged)
    buildkit-image: "public.cr.seqera.io/wave/buildkit:v0.25.2-rootless"

    # New properties for multi-platform
    buildx-image: "public.cr.seqera.io/wave/buildx:v0.20.0"
    buildkitd:
      amd64-addr: "tcp://buildkitd-amd64.wave-build.svc:1234"
      arm64-addr: "tcp://buildkitd-arm64.wave-build.svc:1234"
      client-certs-secret: "buildkitd-client-certs"
```

### New K8s resources

| Resource | Name | Purpose |
|----------|------|---------|
| Secret | `buildkitd-daemon-certs` | TLS certs for buildkitd daemons |
| Secret | `buildkitd-client-certs` | TLS certs for buildx client pods |
| ConfigMap | `buildkitd-config` | `buildkitd.toml` configuration |
| Deployment | `buildkitd-amd64` | Persistent buildkitd on amd64 nodes |
| Deployment | `buildkitd-arm64` | Persistent buildkitd on arm64 nodes |
| Service | `buildkitd-amd64` | ClusterIP endpoint for amd64 daemon |
| Service | `buildkitd-arm64` | ClusterIP endpoint for arm64 daemon |

## Limitations and Future Work

- **POC scope**: Only supports `linux/amd64` + `linux/arm64`. The architecture is extensible
  to additional platforms by adding more buildkitd deployments and `buildx create --append` calls.
- **Singularity**: Multi-platform is not supported for Singularity format (no manifest list
  concept in SIF).
- **Buildkitd scaling**: Each deployment starts with 1 replica. For high build concurrency,
  increase replicas (buildx will round-robin across pods behind the Service).
- **Cache persistence**: Initial design uses `emptyDir` for buildkitd data. For production,
  consider PVCs or StatefulSets to persist cache across pod restarts.
- **Monitoring**: Add Prometheus metrics export from buildkitd (buildkitd supports
  `--debugAddress` for pprof/metrics).
- **Credential rotation**: For production, implement automatic credential refresh for the
  buildkitd daemons (e.g. via IRSA or a sidecar).

## Files to Modify

| File | Change |
|------|--------|
| `wave-api/.../SubmitContainerTokenRequest.java` | Add `multiPlatform` field |
| `wave-api/.../SubmitContainerTokenRequest.java` | Add `copyWith` and builder support |
| `.../BuildRequest.groovy` | Add `multiPlatform` field |
| `.../BuildConfig.groovy` | Add `buildxImage`, `buildkitdAmd64Addr`, `buildkitdArm64Addr` |
| `.../BuildStrategy.groovy` | Add `multiPlatformLaunchCmd()` method |
| `.../KubeBuildStrategy.groovy` | Branch on `multiPlatform` in `build()` |
| `.../K8sService.groovy` | Add `launchMultiPlatformBuildJob()` interface method |
| `.../K8sServiceImpl.groovy` | Implement `launchMultiPlatformBuildJob()` + job spec |
| `.../ContainerController.groovy` | Validate `multiPlatform`, pass to `makeBuildRequest()` |
| `.../ContainerHelper.groovy` | Include `multiPlatform` in containerId hash |
| `application.yml` | Add new config properties |
