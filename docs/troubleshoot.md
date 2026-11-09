---
title: Troubleshoot
description: Diagnose common Wave registry, pull, build, and self-hosted deployment failures.
tags: [wave, troubleshooting, registry, build, self-hosted, docker-compose, kubernetes, eks]
---

This page lists common Wave errors, grouped by where they occur. Each entry states which deployments it applies to.

| Deployment | Description |
| :--------- | :---------- |
| **Hosted Wave** | The Seqera-hosted service at `wave.seqera.io`. The default for Seqera Cloud, and for Seqera Enterprise when `WAVE_SERVER_URL` is left at its default. |
| **Wave Lite (Docker Compose)** | Self-hosted on a single Docker host. Container augmentation, inspection, and private registry authentication only. See [Install Wave Lite with Docker Compose](install/docker-compose.md). |
| **Wave Lite (Kubernetes)** | Self-hosted on any conformant Kubernetes cluster. Same capabilities as Docker Compose. See [Install Wave Lite on Kubernetes](install/kubernetes-lite.md). |
| **Wave (Amazon EKS)** | Self-hosted Wave Lite on Amazon EKS with builds, freeze, mirroring, and scanning enabled. See [Enable Wave builds](install/aws-build.md). |

Builds, freeze, mirroring, and security scanning run only on hosted Wave and on self-hosted Wave on Amazon EKS. Wave Lite cannot run them on either Docker Compose or Kubernetes.

## Find the logs

Where you look depends on the deployment:

- **Hosted Wave**: You cannot access the service logs. Use the error in the Seqera Platform run details or the Nextflow log, and the build, mirror, or scan page linked from the error. When you contact Seqera support, include the container request token or build ID from the error.
- **Wave Lite (Docker Compose)**: Run `docker compose logs wave` on the Docker host.
- **Wave Lite (Kubernetes)**: Run `kubectl logs deployment/wave -n wave`.
- **Wave (Amazon EKS)**: Check the Wave service with `kubectl logs deployment/wave -n wave`. Build, scan, and mirror pods run in the `wave-build` namespace. List them with `kubectl get pods -n wave-build` and read one with `kubectl logs <pod-name> -n wave-build`.

## Registry access

#### `Container image '<image>' does not exist or access is not authorized`

**Applies to:** All deployments.

Seqera Platform reports this error as `Invalid credentials for container registry` in the run error details. The Wave API returns it as an HTTP `400`:

```
POST https://wave.seqera.io/v1alpha2/container [400]
{"message":"Container image '<image>:<tag>' does not exist or access is not authorized"}
```

Before Wave returns a container, it looks up the image digest in the source registry. Wave returns this error for any failure in that lookup, so the message does not tell you whether credentials, the network, or the image reference caused it. Common causes:

- The image or tag does not exist.
- The registry credentials are wrong, or Wave looked them up in a different workspace.
- Wave cannot reach the registry over the network.
- The registry uses a certificate signed by a private certificate authority (CA).
- The registry sends an authentication challenge with an uppercase scheme, such as `BASIC realm="..."` from Sonatype Nexus. Wave 1.44.2 and later parse the scheme case-insensitively. If you self-host an earlier version, upgrade Wave.

To diagnose the cause, check in this order:

1. **Confirm the image exists.** From a machine that can reach the registry, run `docker manifest inspect <image>:<tag>`.
1. **Confirm the credentials.** In your Seqera workspace, select **Credentials** and check that the container registry credential's hostname matches the registry in the image reference. Wave matches credentials by registry hostname and uses the first match. Test the same username and token with `docker login <registry>`.
1. **Confirm the workspace.** Wave looks up credentials in the workspace that `tower.workspaceId` sets in your Nextflow configuration. Without it, Wave uses your personal workspace, which may not hold the credential.
1. **Confirm that Wave can reach the registry.** See the section for your deployment below.

**Hosted Wave**

Hosted Wave connects to your registry from Seqera's network, not from yours. It can reach a registry only if one of these is true:

- The registry is publicly reachable.
- The registry firewall allows the `egress` addresses published at [`meta.seqera.io`](https://meta.seqera.io).

Hosted Wave cannot reach a registry that is exposed only through a private endpoint, such as an Azure Container Registry with public network access disabled. The connection fails before Wave evaluates credentials, so valid credentials do not help. Hosted Wave also cannot pull from a registry whose certificate is signed by a private CA, because you cannot change its trust store.

If your registry is private, deploy self-hosted Wave inside your network, where it reaches the registry directly. See [Self-hosted Wave](install/index.md).

An unreachable registry does not always produce this error. A pipeline run or Studio session can instead stay in a starting state with no error, and no pull appears in the registry logs. See [Wave cannot reach your container registry](https://docs.seqera.io/platform-enterprise/troubleshooting_and_faqs/troubleshooting#wave-cannot-reach-your-container-registry) in the Seqera Platform documentation.

**Self-hosted Wave (all deployments)**

The Wave service logs the cause of every failed lookup:

```
Unable to retrieve digest for image '<image>' -- cause: <cause>
Unable to retrieve digest for image '<image>' -- response status=<status>; headers: ...
```

A connection or name-resolution error in `<cause>` means Wave cannot reach the registry. A `401` or `403` status means Wave reached the registry and the credentials were rejected.

To test reachability from where Wave runs:

- **Docker Compose**: On the Docker host, run `curl -sI https://<registry>/v2/`.
- **Kubernetes and Amazon EKS**: Run a one-off pod in the Wave namespace:

    ```bash
    kubectl run registry-check -n wave --rm -it --restart=Never \
      --image=curlimages/curl -- curl -sI https://<registry>/v2/
    ```

A `200` or `401` response means the registry is reachable. A timeout or name-resolution error points to DNS, a firewall, a network policy, or an egress proxy. See [Egress proxy](install/reference.md#egress-proxy).

Self-hosted Wave takes registry credentials from two sources:

- Requests that carry a Seqera Platform access token use the workspace credentials, the same as hosted Wave.
- Anonymous requests use the static credentials under `wave.registries.<host>`. These live in `config.yml` for Docker Compose and in the `wave-cfg` ConfigMap for Kubernetes and Amazon EKS. See [Container registry](install/reference.md#container-registry).

A self-hosted deployment can pull from a registry that uses a private CA once the CA certificate is in the Wave container's trust store. Wave does not yet document this procedure. Contact Seqera support.

#### Registry push and authentication failures

**Applies to:** Hosted Wave (freeze and mirror to your own repository), Wave (Amazon EKS).

When Wave cannot push a built, frozen, or mirrored image, or cannot authenticate to the target registry, the failure usually matches one of these symptoms:

- **No credentials match the target host.** Wave returns an authentication error when it requests a registry token, before BuildKit or Skopeo starts. This is the fastest failure to diagnose. Confirm you configured credentials for the target registry. For freeze, the credential must be in the Seqera Platform workspace of the request and must have push access to the build repository.
- **The repository does not exist and the registry requires pre-creation.** The push fails with `403 Forbidden` or `404 Not Found` partway through the layer upload, often after an initial `HEAD` succeeds and the final manifest `PUT` fails. Pre-create the repository. For per-registry rules, see [Registry pre-creation](install/reference.md#registry-pre-creation).
- **The credentials exist but lack push scope.** The push typically returns `403` on the final manifest `PUT`, even though the layer uploads appear to work. Check that the credential has `push`, `write`, or `deploy` permission.
- **The repository key is missing from the path (JFrog Artifactory).** The push fails with `404`. Confirm the repository key is the first path segment after the host, for example `artifactory.example.com/docker-local/...`.
- **The AWS credentials or role target the wrong region or account (Amazon EKS only).** For ECR, a misaligned `aws.region` or jump-role configuration produces STS `AccessDenied` errors in the Wave service logs. These appear when Wave exchanges the configured credentials for an ECR authentication token, before the build pod starts.

## Container pulls

#### `Request exceeded pull rate limit` or `Request exceeded build rate limit`

**Applies to:** Hosted Wave, and self-hosted deployments with rate limits enabled.

Wave returns HTTP `429` when a client exceeds its request quota.

- **Hosted Wave**: Anonymous requests have much lower limits than requests that carry a Seqera Platform access token. Set `tower.accessToken` in your Nextflow configuration, or the `TOWER_ACCESS_TOKEN` environment variable, so Nextflow authenticates its Wave requests. See [API limits](how-wave-works.md#api-limits).
- **Self-hosted Wave**: The `rate-limit.*` settings set the limits. They apply only when `MICRONAUT_ENVIRONMENTS` includes `rate-limit`. See [Rate limits](install/reference.md#rate-limits).

#### `Unknown Wave container for token '<token>'`

**Applies to:** All deployments.

The container runtime pulled a `<wave-host>/wt/<token>/...` image after its token expired. Wave container tokens are short-lived. A token tied to a Seqera Platform run expires shortly after the run completes and never lasts longer than 48 hours. Other tokens last 36 hours. See [Ephemeral token access](how-wave-works.md#ephemeral-token-access).

To resolve, request the container again, for example by relaunching the run. Don't store Wave token URLs for later use. To keep an image, use [freeze](features/container-freezes.mdx) to push it to your own registry.

#### `MANIFEST_UNKNOWN` when pulling an augmented container by digest

**Applies to:** All deployments that serve augmented containers.

A task fails to start with an error similar to:

```
CannotPullContainerError: ... manifest for <wave-host>/wt/<token>/<image>@sha256:<digest> not found: manifest unknown
```

This is a known issue. When Wave augments a container, it caches the augmented manifest for one hour. A pull by tag rebuilds the manifest if the cache entry has expired, but a pull by digest does not. The token is still valid, but the digest lookup fails. AWS Batch hits this because the ECS agent resolves the tag to a digest when the task arrives, and later pulls by that digest. If the pull starts more than an hour after Wave last cached the manifest, it fails.

To work around it:

- Retry the task. A new attempt resolves the image by tag again. For example, set `errorStrategy = 'retry'` on the affected processes.
- Use [freeze](features/container-freezes.mdx) to push the image to your own registry, so the runtime pulls it directly instead of through Wave.

#### `Container '<token>' is a freeze/mirror image and must be pulled directly from '<image>'`

**Applies to:** Wave 1.37.0 and later (hosted Wave and Wave on Amazon EKS).

Wave serves frozen and mirrored images only from the target registry, not through its `/wt/<token>/` proxy path. Pull the image reference named in the error. If a script or tool stored the token URL from the response, update it to use the `targetImage` reference instead.

#### Tasks fail to pull images from `localhost:9090`

**Applies to:** Wave Lite (Docker Compose), Wave Lite (Kubernetes), Wave (Amazon EKS).

Wave builds container token URLs from `WAVE_SERVER_URL`. If it is unset, Wave issues tokens that point at `http://localhost:9090`, which compute nodes cannot reach. Set `WAVE_SERVER_URL` to the HTTPS address that Seqera Platform and your compute environments use to reach Wave, then restart Wave.

## Builds

Builds run only on hosted Wave and on Wave (Amazon EKS). On Wave Lite, a build request fails with `Build service is not enabled`. See [Self-hosted configuration](#self-hosted-configuration).

#### `Container build did not complete successfully`

**Applies to:** Hosted Wave, Wave (Amazon EKS).

The error links to the build page, `<wave-host>/view/builds/<build-id>`. Wave can also email the link when the build completes. The build page shows the build log and exit status. If `wave.capabilities.web-views` is `false` on a self-hosted deployment, the page returns `404`. Fetch the log from the API instead, at `GET /v1alpha1/builds/<build-id>/logs`.

Common causes:

- **Exit status `137`**: The build ran out of memory. Hosted Wave builds use up to about 3.6 GB. On Amazon EKS, raise `wave.build.k8s.resources.requests.memory`. For large Conda environments, split the packages across smaller images.
- **The build times out**: Hosted Wave cancels builds after 15 minutes, or 25 minutes with an access token and freeze enabled. See [Time limits](how-wave-works.md#time-limits). On Amazon EKS, `wave.build.timeout` and `wave.build.trusted-timeout` set the limits.
- **A package or base image cannot be resolved**: The log shows the Conda, Pixi, or Docker error. Check the package names, versions, and channels.
- **A registry or proxy error during push**: See [Build errors from registries behind a reverse proxy](#build-errors-from-registries-behind-a-reverse-proxy) and [Registry push and authentication failures](#registry-push-and-authentication-failures).

#### `Package type '<type>' not supported by '<template>' build template`

**Applies to:** Hosted Wave, Wave (Amazon EKS).

The build template you selected does not accept the package type in the request. For example, `cran/installr:v1` accepts only CRAN packages, and `conda/pixi:v1-lock` requires a Pixi lock file. Choose a template that matches your packages. See [Container builds](features/container-builds.mdx).

#### `Singularity build is only allowed enabling freeze mode - see 'wave.freeze' setting`

**Applies to:** Hosted Wave, Wave (Amazon EKS).

Wave builds Singularity images only in freeze mode. Set `wave.freeze = true` and a build repository (`wave.build.repository`) in your Nextflow configuration. See [Nextflow configuration](nextflow/configuration.mdx).

#### `Container security scan did not complete successfully`

**Applies to:** Hosted Wave, Wave (Amazon EKS).

With `wave.scan.mode = 'required'`, Nextflow runs a task only if the scan finds no vulnerabilities above `wave.scan.allowedLevels`. The error links to the scan report at `<wave-host>/view/scans/<scan-id>`. Fix the vulnerable packages, widen `wave.scan.allowedLevels`, or set `wave.scan.mode = 'async'` to scan without blocking tasks. See [Scan](nextflow/configuration.mdx#scan).

#### Build errors from registries behind a reverse proxy

**Applies to:** Hosted Wave, Wave (Amazon EKS).

If your build repository, or a registry your build pulls a base image from, sits behind a reverse proxy such as Cloudflare, the build can fail with the proxy's error instead of the registry's. BuildKit reports the proxy's HTTP status, and the build page shows that single line. Use these signals to find the source:

| Signal | Likely source |
| :----- | :------------ |
| Error on `PUT` or `POST` to your build repository | Your registry or its reverse proxy |
| Error on `FROM` or a base image pull | The source image registry, such as Docker Hub or ECR |
| Fails consistently, and only for large images | Proxy timeout on a large layer upload |
| Fails intermittently across pipelines | Registry rate limit or quota, or a transient proxy issue |
| The failing URL contains your Wave host | Wave itself. On hosted Wave, contact Seqera support. |

To check whether Cloudflare is in the path, run `dig <registry-hostname> +short`. A CNAME ending in `.cdn.cloudflare.net`, or addresses in Cloudflare's ranges, means requests pass through Cloudflare. Cloudflare returns its own status codes:

| Status | Meaning | Resolution |
| :----- | :------ | :--------- |
| `520` | The registry returned an invalid or unexpected response to Cloudflare, often a large layer timing out on the registry side. | Split large Conda environments into smaller images. To confirm the proxy is the cause, build to a repository that is not behind a proxy, such as a direct ECR endpoint. |
| `522` | Cloudflare could not connect to the registry backend. The registry is overloaded or offline, or a firewall blocks Cloudflare's addresses. | Ask your network team to confirm the backend is reachable from Cloudflare's IP ranges. |
| `524` | The registry accepted the connection but did not respond before Cloudflare's timeout. Common with large layers. | Split large Conda environments into smaller images. |

These are not Wave or BuildKit errors. For example, a `520` appears in the build log as:

```
error: failed to copy: unexpected status from PUT request to
https://<registry>/v2/<namespace>/blobs/uploads/<id>: 520 <none>
```

#### Builds fail on Bottlerocket nodes

**Applies to:** Wave (Amazon EKS).

BuildKit requires user namespaces, and Bottlerocket sets `user.max_user_namespaces=0` by default. Every build fails on an otherwise correct [Wave build deployment](install/aws-build.md). To resolve, raise the limit on your build nodes. A value of `63359` is reasonable. Values that are too low cap concurrent build capacity and cause intermittent failures.

Set the value at boot through the node group's startup script or user data. This is the preferred route because it needs no privileged containers. If you cannot change the node configuration directly, apply it with a DaemonSet scoped to the build nodes:

```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  labels:
    app: sysctl-userns
  name: sysctl-userns
spec:
  selector:
    matchLabels:
      app: sysctl-userns
  template:
    metadata:
      labels:
        app: sysctl-userns
    spec:
      containers:
        - name: sysctl-userns
          image: busybox
          command: ["sh", "-euxc", "sysctl -w user.max_user_namespaces=63359 && sleep infinity"]
          securityContext:
            privileged: true
      affinity:
        nodeAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            nodeSelectorTerms:
              - matchExpressions:
                  - key: service
                    operator: In
                    values: ["wave-build", "wave-build-arm64"]
```

For more on Bottlerocket settings, see the [Bottlerocket FAQs](https://bottlerocket.dev/en/faq/).

#### Builds succeed but are reported as failed on Kubernetes 1.34 and later

**Applies to:** Wave (Amazon EKS) earlier than 1.38.0.

On a Kubernetes 1.34 or later cluster, Wave versions before 1.38.0 report every build, scan, and mirror job as failed, with exit status `-1`, even though the image was built and pushed. The build status returns `COMPLETED` with `succeeded: false`, and Nextflow stops the run. The bundled Kubernetes client cannot parse the pod status that Kubernetes 1.34 returns.

To resolve, upgrade Wave to 1.38.0 or later.

## Self-hosted configuration

#### `Build service is not enabled - Check Wave configuration setting 'wave.build.enabled'`

**Applies to:** Wave Lite (Docker Compose), Wave Lite (Kubernetes), Wave (Amazon EKS) with builds disabled.

The request needs a container build, and the deployment does not run builds. Wave returns HTTP `501`. Requests that need a build include:

- Conda, Pixi, or CRAN packages, for example a Nextflow process with a `conda` directive and `wave.strategy` that includes `conda`.
- A Dockerfile or Singularity recipe.
- Freeze mode (`wave.freeze = true`).

The related errors `Security scan service is not enabled - Check Wave configuration setting 'wave.scan.enabled'` and `Mirror service is not enabled - Check Wave configuration setting 'wave.mirror.enabled'` have the same cause for scan and mirror requests.

To resolve on Wave Lite, remove the build, freeze, scan, or mirror settings from your pipeline configuration and use prebuilt images. Wave Lite cannot run these features on either Docker Compose or Kubernetes, and you cannot extend a Docker Compose deployment to the full configuration. To add them, install Wave Lite on Amazon EKS and [enable Wave builds](install/aws-build.md). On Wave (Amazon EKS), check that the setting named in the error is enabled.

#### `Ephemeral container provisioning is not enabled in this Wave deployment - use 'freeze' mode to provision this container`

**Applies to:** Self-hosted deployments with `wave.capabilities.ephemeral-token: false`, or with `strict` in `MICRONAUT_ENVIRONMENTS`.

The deployment does not serve augmented containers through Wave, so every container must be provisioned with freeze. Set `wave.freeze = true` in your Nextflow configuration. Freeze requires builds, so on Wave Lite this setting leaves no way to provision containers. On Wave Lite, set `wave.capabilities.ephemeral-token: true`, or remove `strict` and set the individual [capabilities](install/reference.md#capabilities) you need.

#### `Missing user access token`

**Applies to:** Self-hosted deployments with `wave.capabilities.anonymous-access: false`.

The deployment requires authentication, and the request did not carry a Seqera Platform access token. Set `tower.accessToken` in your Nextflow configuration, or the `TOWER_ACCESS_TOKEN` environment variable. Wave CLI users pass `--tower-token`. Runs launched from Seqera Platform send the token automatically.

#### `Unauthorized - Make sure you have provided a valid access token`

**Applies to:** All deployments.

Seqera Platform rejected the access token sent with the request. The token is invalid, expired, or revoked, or it belongs to a different Seqera Platform installation than the one Wave is paired with. Generate a new token in Seqera Platform and update your Nextflow configuration or Wave CLI settings.

#### `Missing pairing record for Tower endpoint '<url>'`

**Applies to:** Wave Lite (Docker Compose), Wave Lite (Kubernetes), Wave (Amazon EKS).

Wave has no pairing with the Seqera Platform endpoint in the request. Seqera Platform pairs with Wave when it connects to it, and Wave records the Platform API endpoint it paired with. Check that:

- Seqera Platform has `TOWER_ENABLE_WAVE=true`, and `WAVE_SERVER_URL` set to your Wave address.
- Seqera Platform can reach Wave. Run `curl https://<wave-host>/service-info` from the Platform host.
- The Platform endpoint the client sends matches the endpoint Platform paired with. Nextflow sends `tower.endpoint`. A different hostname for the same installation, such as an internal address, does not match.
- `TOWER_ENDPOINT_URL` in the Wave configuration is the Platform API endpoint, for example `https://platform.example.com/api`. Wave uses it for requests that do not name an endpoint.

When Seqera Platform cannot reach Wave, launches fail on the Platform side with `Wave service connection is not active`. See [`Wave service connection is not active`](https://docs.seqera.io/platform-enterprise/troubleshooting_and_faqs/preflight_checks_troubleshooting#wave-service-connection-is-not-active) in the Seqera Platform documentation.

## Local development

#### BuildKit fails to start on Docker Desktop for macOS

**Applies to:** Wave running builds through a local Docker daemon on Apple silicon, for example during Wave development.

The build fails with:

```
could not connect to unix:///run/user/1000/buildkit/buildkitd.sock after 10 trials
========== log ==========
[rootlesskit:parent] error: failed to start the child: fork/exec /proc/self/exe: invalid argument
sh: can't kill pid 14: No such process
```

Rootless BuildKit cannot start under `linux/amd64` emulation. Request an `arm64` build instead:

- With the Wave CLI, add `--platform linux/arm64` to the build command.
- With the API, set `containerPlatform: linux/arm64` in the request body.

## Related

- [Wave cannot reach your container registry](https://docs.seqera.io/platform-enterprise/troubleshooting_and_faqs/troubleshooting#wave-cannot-reach-your-container-registry) (Seqera Platform)
- [Registries behind a private endpoint](https://docs.seqera.io/platform-enterprise/enterprise/configuration/wave#registries-behind-a-private-endpoint) (Seqera Platform)
- [Fusion Snapshots troubleshooting](https://docs.seqera.io/fusion/troubleshooting/fusion-snapshots) (Fusion)
- [FAQ](faq.md)
