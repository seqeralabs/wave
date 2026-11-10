---
title: Troubleshooting
description: Errors and resolutions for hosted and self-hosted Wave, covering registry access, container pulls, builds, and deployment configuration.
tags: [wave, troubleshooting, registry, build, self-hosted, docker-compose, kubernetes, eks]
---

When you use Wave, you might encounter the following issues. Each entry states which deployments it applies to:

| Deployment | Description |
| :--------- | :---------- |
| **Hosted Wave** | The Seqera-hosted service at `wave.seqera.io`. The default for Seqera Platform Cloud, and for Seqera Platform Enterprise when `WAVE_SERVER_URL` keeps its default value. |
| **Wave Lite (Docker Compose)** | Self-hosted on a single Docker host. Provides container augmentation, inspection, and private registry authentication. See [Install Wave Lite with Docker Compose](install/docker-compose.md). |
| **Wave Lite (Kubernetes)** | Self-hosted on any conformant Kubernetes cluster, with the same capabilities as Docker Compose. See [Install Wave Lite on Kubernetes](install/kubernetes-lite.md). |
| **Wave (Amazon EKS)** | Self-hosted Wave on Amazon EKS, with builds, freeze, mirroring, and security scanning enabled. See [Enable Wave builds](install/aws-build.md). |

Only hosted Wave and Wave (Amazon EKS) run builds, freeze, mirroring, and security scanning.

## Find the logs

The logs to check depend on the deployment:

| Deployment | Where to look |
| :--------- | :------------ |
| Hosted Wave | The error in the Seqera Platform run details or the Nextflow log, and the build, mirror, or scan page that the error links to. The service logs are not available to you. When you contact Seqera support, include the container token or build ID from the error. |
| Wave Lite (Docker Compose) | `docker compose logs wave` on the Docker host. |
| Wave Lite (Kubernetes) | `kubectl logs deployment/wave -n wave` |
| Wave (Amazon EKS) | `kubectl logs deployment/wave -n wave` for the Wave service. Build, scan, and mirror pods run in the `wave-build` namespace. List them with `kubectl get pods -n wave-build` and read one with `kubectl logs <pod-name> -n wave-build`. |

## Registry access

### Error: `Container image '<image>' does not exist or access is not authorized`

**Applies to:** All deployments.

Seqera Platform shows this error as `Invalid credentials for container registry` in the run error details. The Wave API returns it with HTTP status `400`:

```
POST https://wave.seqera.io/v1alpha2/container [400]
{"message":"Container image '<image>:<tag>' does not exist or access is not authorized"}
```

This issue occurs when Wave cannot look up the image digest in the source registry. Wave returns the same error for every lookup failure. The message does not say whether the credentials, the network, or the image reference caused it.

| Cause | How to check |
| :---- | :----------- |
| The image or tag does not exist. | From a machine that can reach the registry, run `docker manifest inspect <image>:<tag>`. |
| The registry credentials are wrong. | In your Seqera workspace, select **Credentials**. Check that the container registry credential's hostname matches the registry in the image reference. Wave matches credentials by longest matching registry+repository prefix with bare-hostname as fallback and uses the first match. Test the same username and token with `docker login <registry>`. |
| Wave looked up the credentials in a different workspace. | Wave uses the workspace that `tower.workspaceId` sets in your Nextflow configuration. Without it, Wave uses your personal workspace. |
| Wave cannot reach the registry. | See [Hosted Wave](#hosted-wave) or [Self-hosted Wave](#self-hosted-wave) below. |
| The registry certificate is signed by a private certificate authority (CA). | Hosted Wave cannot trust a private CA. Self-hosted Wave needs the CA certificate in the Wave container's trust store. This procedure is not documented yet. Contact Seqera support. |
| The registry sends an uppercase authentication scheme, such as `BASIC realm="..."` from Sonatype Nexus. | Wave 1.44.2 and later accept the scheme in any case. To resolve on a self-hosted deployment, upgrade Wave. |

#### Hosted Wave

Hosted Wave connects to your registry from Seqera's network, not from yours. It reaches the registry only when the registry is publicly reachable, or when the registry firewall allows the `egress` addresses published at [`meta.seqera.io`](https://meta.seqera.io).

Hosted Wave cannot reach a registry that is exposed only through a private endpoint, such as an Azure Container Registry with public network access disabled. The connection fails before Wave evaluates the credentials. Valid credentials do not change the result.

To resolve for a private registry, deploy self-hosted Wave inside your network, where it reaches the registry directly. See [Self-hosted Wave](install/index.md).

An unreachable registry does not always return this error. A pipeline run or Studio session can instead stay in a starting state with no error, and the registry logs show no pull. See [Wave cannot reach your container registry](https://docs.seqera.io/platform-enterprise/troubleshooting_and_faqs/troubleshooting#wave-cannot-reach-your-container-registry).

#### Self-hosted Wave

The Wave service logs the cause of every failed lookup:

```
Unable to retrieve digest for image '<image>' -- cause: <cause>
Unable to retrieve digest for image '<image>' -- response status=<status>; headers: ...
```

A connection or name-resolution error in `<cause>` means Wave cannot reach the registry. A `401` or `403` status means Wave reached the registry and the registry rejected the credentials.

To test reachability from where Wave runs:

- **Docker Compose**: On the Docker host, run `curl -sI https://<registry>/v2/`.
- **Kubernetes and Amazon EKS**: Run a one-off pod in the Wave namespace:

    ```bash
    kubectl run registry-check -n wave --rm -it --restart=Never \
      --image=curlimages/curl -- curl -sI https://<registry>/v2/
    ```

A `200` or `401` response means the registry is reachable. A timeout or name-resolution error points to DNS, a firewall, a network policy, or an egress proxy. See [Egress proxy](install/reference.md#egress-proxy).

Self-hosted Wave takes registry credentials from one of two sources:

- Requests that carry a Seqera Platform access token use the workspace credentials, the same as hosted Wave.
- Anonymous requests use the static credentials under `wave.registries.<host>`. Docker Compose deployments set these in `config.yml`. Kubernetes and Amazon EKS deployments set them in the `wave-cfg` ConfigMap. See [Container registry](install/reference.md#container-registry).

### Registry push and authentication failures

**Applies to:** Hosted Wave (freeze and mirror to your own repository), Wave (Amazon EKS).

When Wave cannot push a built, frozen, or mirrored image, or cannot authenticate to the target registry, check these causes:

| Symptom | Cause | Resolution |
| :------ | :---- | :--------- |
| An authentication error at token-request time, before BuildKit or Skopeo starts. | No credentials match the target host. | Configure credentials for the target registry. For freeze, add a credential with push access to the build repository in the Seqera Platform workspace of the request. |
| `403 Forbidden` or `404 Not Found` partway through the layer upload. An initial `HEAD` often succeeds and the final manifest `PUT` fails. | The repository does not exist, and the registry requires you to create it first. | Create the repository. For per-registry rules, see [Registry pre-creation](install/reference.md#registry-pre-creation). |
| `403` on the final manifest `PUT`, even though the layer uploads appear to work. | The credentials lack push scope. | Grant the credential `push`, `write`, or `deploy` permission. |
| `404` on push to JFrog Artifactory. | The repository key is missing from the path. | Make the repository key the first path segment after the host, for example `artifactory.example.com/docker-local/...`. |
| STS `AccessDenied` errors in the Wave service logs, before the build pod starts (Amazon EKS only). | The AWS credentials or role target the wrong region or account for ECR. | Align `aws.region` and the jump-role configuration with the ECR registry. |

## Container pulls

### Error: `Request exceeded pull rate limit` or `Request exceeded build rate limit`

**Applies to:** Hosted Wave, and self-hosted deployments with rate limits enabled.

Wave returns HTTP status `429`. This issue occurs when a client exceeds its request quota.

- **Hosted Wave**: Anonymous requests have much lower limits than authenticated requests. To resolve, set `tower.accessToken` in your Nextflow configuration, or the `TOWER_ACCESS_TOKEN` environment variable. Nextflow then authenticates its Wave requests. See [API limits](how-wave-works.md#api-limits).
- **Self-hosted Wave**: The `rate-limit.*` settings define the limits. They apply only when `MICRONAUT_ENVIRONMENTS` includes `rate-limit`. See [Rate limits](install/reference.md#rate-limits).

### Error: `Unknown Wave container for token '<token>'`

**Applies to:** All deployments.

This issue occurs when the container runtime pulls a `<wave-host>/wt/<token>/...` image after its token expires. A token tied to a Seqera Platform run expires shortly after the run completes, and never lasts longer than 48 hours. Other tokens last 36 hours. See [Ephemeral token access](how-wave-works.md#ephemeral-token-access).

To resolve, request the container again, for example by relaunching the run. Do not store Wave token URLs for later use. To keep an image, use [freeze](features/container-freezes.mdx) to push it to your own registry.

### Error: `MANIFEST_UNKNOWN` when pulling an augmented container by digest

**Applies to:** All deployments that serve augmented containers.

A task fails to start with an error similar to:

```
CannotPullContainerError: ... manifest for <wave-host>/wt/<token>/<image>@sha256:<digest> not found: manifest unknown
```

This is a known issue. Wave caches an augmented manifest for one hour. A pull by tag rebuilds an expired manifest, but a pull by digest does not. The token is still valid, but the digest lookup fails.

AWS Batch is affected because the ECS agent resolves the tag to a digest when the task arrives, then pulls by that digest. The pull fails if it starts more than an hour after Wave last cached the manifest.

Workarounds:

- Retry the task. A new attempt resolves the image by tag again. For example, set `errorStrategy = 'retry'` on the affected processes.
- Use [freeze](features/container-freezes.mdx) to push the image to your own registry. The runtime then pulls it directly instead of through Wave.

### Error: `Container '<token>' is a freeze/mirror image and must be pulled directly from '<image>'`

**Applies to:** Wave 1.37.0 and later, on hosted Wave and Wave (Amazon EKS).

This issue occurs when a client pulls a frozen or mirrored image through the Wave `/wt/<token>/` proxy path. Wave serves these images only from the target registry.

To resolve, pull the image reference named in the error. If a script or tool stored the token URL, update it to use the `targetImage` reference from the Wave response.

### Tasks fail to pull images from `localhost:9090`

**Applies to:** Wave Lite (Docker Compose), Wave Lite (Kubernetes), Wave (Amazon EKS).

This issue occurs when `WAVE_SERVER_URL` is unset. Wave builds container token URLs from this value, and without it the URLs point at `http://localhost:9090`, which compute nodes cannot reach.

To resolve, set `WAVE_SERVER_URL` to the HTTPS address that Seqera Platform and your compute environments use to reach Wave, then restart Wave.

## Builds

Builds run only on hosted Wave and Wave (Amazon EKS). On Wave Lite, a build request fails with `Build service is not enabled`. See [Self-hosted configuration](#self-hosted-configuration).

### Error: `Container build did not complete successfully`

**Applies to:** Hosted Wave, Wave (Amazon EKS).

The error links to the build page at `<wave-host>/view/builds/<build-id>`, which shows the build log and exit status. Wave can also email the link when the build completes. If `wave.capabilities.web-views` is `false` on a self-hosted deployment, the build page returns `404`. Fetch the log from `GET /v1alpha1/builds/<build-id>/logs` instead.

| Symptom in the build log | Cause | Resolution |
| :----------------------- | :---- | :--------- |
| Exit status `137` | The build ran out of memory. Hosted Wave builds use up to about 3.6 GB. | Split large Conda environments across smaller images. On Amazon EKS, raise `wave.build.k8s.resources.requests.memory`. |
| The build is cancelled after the time limit. | Hosted Wave cancels builds after 15 minutes, or 25 minutes with an access token and freeze enabled. See [Time limits](how-wave-works.md#time-limits). | Reduce the build size. On Amazon EKS, raise `wave.build.timeout` and `wave.build.trusted-timeout`. |
| A Conda, Pixi, or Docker error | A package or base image cannot be resolved. | Check the package names, versions, and channels. |
| An HTTP error on push | The registry or a proxy rejected the push. | See [Build errors from registries behind a reverse proxy](#build-errors-from-registries-behind-a-reverse-proxy) and [Registry push and authentication failures](#registry-push-and-authentication-failures). |

### Error: `Package type '<type>' not supported by '<template>' build template`

**Applies to:** Hosted Wave, Wave (Amazon EKS).

This issue occurs when the selected build template does not accept the package type in the request. For example, `cran/installr:v1` accepts only CRAN packages, and `conda/pixi:v1-lock` requires a Pixi lock file.

To resolve, select a template that matches your packages. See [Container builds](features/container-builds.mdx).

### Error: `Singularity build is only allowed enabling freeze mode...`

**Applies to:** Hosted Wave, Wave (Amazon EKS).

Full message:

```
Singularity build is only allowed enabling freeze mode - see 'wave.freeze' setting
```

This issue occurs when you request a Singularity image without freeze mode. Wave builds Singularity images only in freeze mode.

To resolve, set `wave.freeze = true` and a build repository (`wave.build.repository`) in your Nextflow configuration. See [Nextflow configuration](nextflow/configuration.mdx).

### Error: `Container security scan did not complete successfully`

**Applies to:** Hosted Wave, Wave (Amazon EKS).

This issue occurs when `wave.scan.mode = 'required'` and the scan finds vulnerabilities above `wave.scan.allowedLevels`. Nextflow does not run the task. The error links to the scan report at `<wave-host>/view/scans/<scan-id>`.

To resolve, update the vulnerable packages or widen `wave.scan.allowedLevels`. To scan without blocking tasks, set `wave.scan.mode = 'async'`. See [Scan](nextflow/configuration.mdx#scan).

### Build errors from registries behind a reverse proxy

**Applies to:** Hosted Wave, Wave (Amazon EKS).

This issue occurs when your build repository, or a registry that hosts a base image, sits behind a reverse proxy such as Cloudflare. BuildKit reports the proxy's HTTP status instead of the registry's, and the build page shows only that line. Use these signals to find the source:

| Signal | Likely source |
| :----- | :------------ |
| Error on `PUT` or `POST` to your build repository | Your registry or its reverse proxy |
| Error on `FROM` or a base image pull | The source image registry, such as Docker Hub or ECR |
| Fails consistently, and only for large images | Proxy timeout on a large layer upload |
| Fails intermittently across pipelines | Registry rate limit or quota, or a transient proxy issue |
| The failing URL contains your Wave host | Wave itself. On hosted Wave, contact Seqera support. |

To check whether Cloudflare is in the path, run `dig <registry-hostname> +short`. A CNAME that ends in `.cdn.cloudflare.net`, or addresses in Cloudflare's ranges, means requests pass through Cloudflare.

Cloudflare returns its own status codes. For example, a `520` appears in the build log as:

```
error: failed to copy: unexpected status from PUT request to
https://<registry>/v2/<namespace>/blobs/uploads/<id>: 520 <none>
```

| Status | Cause | Resolution |
| :----- | :---- | :--------- |
| `520` | The registry returned an invalid or unexpected response to Cloudflare, often because a large layer timed out on the registry side. | Split large Conda environments into smaller images. To confirm the proxy is the cause, build to a repository that is not behind a proxy, such as a direct ECR endpoint. |
| `522` | Cloudflare could not connect to the registry backend. The registry is overloaded or offline, or a firewall blocks Cloudflare's addresses. | Ask your network team to confirm the backend is reachable from Cloudflare's IP ranges. |
| `524` | The registry accepted the connection but did not respond before the Cloudflare timeout. Common with large layers. | Split large Conda environments into smaller images. |

### Builds fail on Bottlerocket nodes

**Applies to:** Wave (Amazon EKS).

Every build fails on an otherwise correct [Wave build deployment](install/aws-build.md). This issue occurs because BuildKit requires user namespaces, and Bottlerocket sets `user.max_user_namespaces=0` by default.

To resolve, raise the limit on your build nodes, for example to `63359`. A low value caps concurrent builds and causes intermittent failures.

Set the value at boot through the node group's startup script or user data. This route needs no privileged containers. If you cannot change the node configuration directly, apply the value with a DaemonSet scoped to the build nodes:

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

### Builds succeed but are reported as failed on Kubernetes 1.34 and later

**Applies to:** Wave (Amazon EKS) earlier than 1.38.0.

The image is built and pushed, but Wave reports the build, scan, or mirror job as failed with exit status `-1`. The build status returns `COMPLETED` with `succeeded: false`, and Nextflow stops the run.

This issue occurs because the Kubernetes client bundled with Wave versions before 1.38.0 cannot parse the pod status that Kubernetes 1.34 and later return.

To resolve, upgrade Wave to 1.38.0 or later.

## Self-hosted configuration

### Error: `Build service is not enabled...`

**Applies to:** Wave Lite (Docker Compose), Wave Lite (Kubernetes), Wave (Amazon EKS) with builds disabled.

Full message, returned with HTTP status `501`:

```
Build service is not enabled - Check Wave configuration setting 'wave.build.enabled'
```

This issue occurs when a request needs a container build and the deployment does not run builds. These requests need a build:

- Conda, Pixi, or CRAN packages, for example a Nextflow process with a `conda` directive when `wave.strategy` includes `conda`
- A Dockerfile or Singularity recipe
- Freeze mode (`wave.freeze = true`)

Scan and mirror requests fail the same way, with these messages:

- `Security scan service is not enabled - Check Wave configuration setting 'wave.scan.enabled'`
- `Mirror service is not enabled - Check Wave configuration setting 'wave.mirror.enabled'`

To resolve on Wave Lite, remove the build, freeze, scan, and mirror settings from your pipeline configuration and use prebuilt images. Wave Lite cannot run these features on Docker Compose or on Kubernetes, and a Docker Compose deployment cannot be extended to the full configuration. To add the features, install Wave Lite on Amazon EKS and [enable Wave builds](install/aws-build.md).

To resolve on Wave (Amazon EKS), enable the setting named in the error.

### Error: `Ephemeral container provisioning is not enabled in this Wave deployment...`

**Applies to:** Self-hosted deployments with `wave.capabilities.ephemeral-token: false`, or with `strict` in `MICRONAUT_ENVIRONMENTS`.

Full message:

```
Ephemeral container provisioning is not enabled in this Wave deployment - use 'freeze' mode to provision this container
```

This issue occurs when the deployment does not serve augmented containers. Every container must then be provisioned with freeze.

To resolve on Wave (Amazon EKS), set `wave.freeze = true` in your Nextflow configuration.

To resolve on Wave Lite, set `wave.capabilities.ephemeral-token: true`, or remove `strict` and set only the [capabilities](install/reference.md#capabilities) you need. Freeze requires builds, and Wave Lite does not run builds. With ephemeral provisioning disabled, Wave Lite cannot provision any container.

### Error: `Missing user access token`

**Applies to:** Self-hosted deployments with `wave.capabilities.anonymous-access: false`.

This issue occurs when the deployment requires authentication and the request does not carry a Seqera Platform access token.

To resolve, set `tower.accessToken` in your Nextflow configuration, or the `TOWER_ACCESS_TOKEN` environment variable. With the Wave CLI, pass `--tower-token`. Runs launched from Seqera Platform send the token automatically.

### Error: `Unauthorized - Make sure you have provided a valid access token`

**Applies to:** All deployments.

This issue occurs when Seqera Platform rejects the access token sent with the request. The token is invalid, expired, or revoked, or it belongs to a different Seqera Platform installation than the one Wave is paired with.

To resolve, generate a new access token in Seqera Platform and update your Nextflow configuration or Wave CLI settings.

### Error: `Missing pairing record for Tower endpoint '<url>'`

**Applies to:** Wave Lite (Docker Compose), Wave Lite (Kubernetes), Wave (Amazon EKS).

This issue occurs when Wave has no pairing with the Seqera Platform endpoint in the request. Seqera Platform pairs with Wave when it connects to it, and Wave records the Platform API endpoint it paired with.

To resolve, check that:

- Seqera Platform has `TOWER_ENABLE_WAVE=true`, and `WAVE_SERVER_URL` set to your Wave address.
- Seqera Platform can reach Wave. From the Platform host, run `curl https://<wave-host>/service-info`.
- The client sends the endpoint that Seqera Platform paired with. Nextflow sends `tower.endpoint`. A different hostname for the same installation, such as an internal address, does not match.
- `TOWER_ENDPOINT_URL` in the Wave configuration is the Platform API endpoint, for example `https://platform.example.com/api`. Wave uses it for requests that do not name an endpoint.

When Seqera Platform cannot reach Wave, launches fail on the Platform side. See [`Wave service connection is not active`](https://docs.seqera.io/platform-enterprise/troubleshooting_and_faqs/preflight_checks_troubleshooting#wave-service-connection-is-not-active).

## Local development

### BuildKit fails to start on Docker Desktop for macOS

**Applies to:** Wave running builds through a local Docker daemon on Apple silicon, for example during Wave development.

The build fails with:

```
could not connect to unix:///run/user/1000/buildkit/buildkitd.sock after 10 trials
========== log ==========
[rootlesskit:parent] error: failed to start the child: fork/exec /proc/self/exe: invalid argument
sh: can't kill pid 14: No such process
```

This issue occurs because rootless BuildKit cannot start under `linux/amd64` emulation.

To resolve, request an `arm64` build:

- With the Wave CLI, add `--platform linux/arm64` to the build command.
- With the API, set `containerPlatform: linux/arm64` in the request body.

## Related

- [Wave cannot reach your container registry](https://docs.seqera.io/platform-enterprise/troubleshooting_and_faqs/troubleshooting#wave-cannot-reach-your-container-registry) (Seqera Platform)
- [Registries behind a private endpoint](https://docs.seqera.io/platform-enterprise/enterprise/configuration/wave#registries-behind-a-private-endpoint) (Seqera Platform)
- [Fusion Snapshots troubleshooting](https://docs.seqera.io/fusion/troubleshooting/fusion-snapshots) (Fusion)
- [FAQ](faq.md)
