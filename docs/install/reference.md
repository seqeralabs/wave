---
title: Configuration reference
description: Configuration options for self-hosted Wave deployments.
tags: [configuration, reference, wave]
---

The following options configure self-hosted Wave deployments.
If you use Wave as a hosted service, these options do not apply.

Configure Wave by setting YAML values in the [`config.yml`](https://github.com/seqeralabs/wave/blob/master/config.yml) configuration file:

```yml
wave:
    mail:
        from: "wave-app@seqera.io"
```

Configuration paths in this reference use dot notation to represent nested YAML keys. In this example, the `from` value nested under the `mail` section is referenced as `wave.mail.from`.

You can configure Wave using either the `config.yml` file or environment variables. Environment variables are provided where available, though not all configuration options support them.

## General

Configure general Wave application settings.

`tower.endpoint.url` *(optional)*
: URL of the Seqera Platform API service (default: [`https://api.cloud.seqera.io`](https://api.cloud.seqera.io)).
  Can be set using the `${TOWER_ENDPOINT_URL}` environment variable.

`wave.deny-hosts` *(optional)*
: Hostname patterns to deny. Requests targeting these hosts are rejected.
  Example patterns: `ngrok.app`, `ngrok-free.app`, `//localhost`.

`wave.deny-paths` *(optional)*
: API path patterns to filter out. Requests for matching artifacts, such as non-existent manifests, are rejected.

`wave.server.url` *(required)*
: URL of the Wave server.
  Can be set using the `${WAVE_SERVER_URL}` environment variable.

`wave.tokens.cache.duration` *(optional)*
: Time-to-live of tokens for requests **not** bound to a workflow (builds, mirrors, and anonymous requests). This is also the lifetime used for every request when the workflow watcher is disabled (default: `36h`).

`wave.tokens.cache.max-duration` *(optional)*
: Hard ceiling on how long a workflow-bound ephemeral token can ever live, measured from the request creation time (default: `2d`).

`wave.tokens.cache.access-ttl` *(optional)*
: Short time-to-live granted to a workflow-bound token on each renewal. It bounds how long a container stays accessible after its workflow completes (default: `20m`).

`wave.tokens.cache.refresh-interval` *(optional)*
: How often a workflow-bound token is re-checked and renewed while its workflow is active. Must be shorter than `access-ttl` so several renewals fit within a token's lifetime (default: `180s`).

`wave.tokens.watcher.enabled` *(optional)*
: Whether ephemeral container tokens are bound to the Platform workflow lifecycle. When `false`, all requests keep the fixed `cache.duration` and no workflow-status watcher runs — useful for high-traffic or self-hosted deployments that want to avoid the extra Platform load (default: `true`).

`wave.tokens.watcher.interval` *(optional)*
: Delay between two consecutive watcher runs (default: `10s`).

`wave.tokens.watcher.delay` *(optional)*
: Delay after which the watcher service is launched on bootstrap (randomized) (default: `5s`).

`wave.tokens.watcher.count` *(optional)*
: Maximum number of container requests processed in a single watcher cycle (default: `250`).

## Feature toggles

Each Wave feature is an independent toggle. Wave Lite runs with all four off, which is what the `lite` Micronaut environment applies.

`wave.build.enabled` *(optional)*
: When `true`, Wave provisions containers with on-demand builds (default: `true`). Freeze and scanning both depend on the build pipeline and are unavailable when this is `false`.

`wave.mirror.enabled` *(optional)*
: When `true`, Wave can mirror images into a target repository (default: `true`).

`wave.scan.enabled` *(optional)*
: When `true`, activates vulnerability scanning (default: `false`). Requires `wave.build.enabled` and `wave.scan.reports.path`.

`wave.blob-cache.enabled` *(optional)*
: When `true`, Wave caches container layers in object storage (default: `false`). Requires the blob cache storage settings.

## Capabilities

The `wave.capabilities.*` flags are installation-level toggles for optional Wave capabilities. All default to `true` (permissive). Set a flag to `false` to lock down that capability, or enable the `strict` environment (`MICRONAUT_ENVIRONMENTS=strict`) to disable all of them at once — intended for regulated deployments where Wave must not serve images directly, broker credentials, expose its HTML pages, or accept anonymous requests.

`wave.capabilities.anonymous-access` *(optional)*
: When `true`, anonymous (unauthenticated) users can access the Wave server (default: `true`).
  Set to `false` to require authenticated access, so that every request must carry a Platform-issued token.
  Legacy alias: `wave.allowAnonymous`. Prefer the canonical key; the alias is still honored.

`wave.capabilities.ephemeral-token` *(optional)*
: When `true`, Wave can provision a container by pulling an existing image directly, applying any container configuration on the fly (the augmentation path) (default: `true`).
  Set to `false` to disable this path in locked-down deployments where Wave must not act as an augmenting or pass-through proxy; such requests are rejected and must instead use `freeze` mode to provision the container via an actual image build.

`wave.capabilities.credentials-federation` *(optional)*
: When `true`, Wave brokers the resolved workspace registry credentials when proxying image pulls (default: `true`).
  When `false`, Wave does not broker registry credentials on the proxy pull path, so images are pulled directly from the target registry using the caller's own credentials (for example an EC2 instance profile or IAM role). Build, inspect, and augmentation flows are unaffected.

`wave.capabilities.web-views` *(optional)*
: When `true`, the HTML view pages served under `/view/**` (build, mirror, scan, container, and inspect pages) are enabled (default: `true`).
  Set to `false` to disable these pages globally, in which case all `/view/**` routes return `404`.

## Container registry

Wave uses the generic format `wave.registries.<REGISTRY_NAME>.username` and `wave.registries.<REGISTRY_NAME>.password` for registry authentication.
You must specify all repositories used in your Wave installation.

The following examples show standard formats for known registries, but you can customize the registry name (for example, change `azurecr.io` to `seqeralabs.azurecr.io`).

The environment variables noted below are not read directly by Wave: they take effect only where your `config.yml` interpolates them, as in `username: "${DOCKER_USER:}"`. Setting `DOCKER_USER` without such an entry has no effect.

Configure container registry authentication with the following options.

`wave.registries.<AWS_ECR_REPO_NAME>.password` *(optional)*
: AWS ECR password for authentication.
  For example: `195996028523.dkr.ecr.eu-west-1.amazonaws.com`.
  Can be set using the `${AWS_SECRET_ACCESS_KEY}` environment variable.

`wave.registries.<AWS_ECR_REPO_NAME>.username` *(optional)*
: AWS ECR (Elastic Container Registry) username for authentication.
  For example, `195996028523.dkr.ecr.eu-west-1.amazonaws.com`.
  Can be set using the `${AWS_ACCESS_KEY_ID}` environment variable.

`wave.registries.default` *(optional)*
: Default container registry for Wave (default: `docker.io`).

`wave.registries.docker.io.password` *(optional)*
: Docker Hub password or PAT (Personal Access Token) for authentication.
  Can be set using the `${DOCKER_PAT}` environment variable.

`wave.registries.docker.io.username` *(optional)*
: Docker Hub username for authentication.
  Can be set using the `${DOCKER_USER}` environment variable.

`wave.registries.quay.io.password` *(optional)*
: Quay.io password or PAT for authentication.
  Can be set using the `${QUAY_PAT}` environment variable.

`wave.registries.quay.io.username` *(optional)*
: Quay.io username for authentication.
  Can be set using the `${QUAY_USER}` environment variable.

## Registry pre-creation

Wave pushes with BuildKit for builds and Skopeo for mirrors, so whether a target repository must exist beforehand is the registry's rule, not Wave's. If the registry requires pre-creation and the path is missing, the push fails partway through the layer upload. See [Registry push and authentication failures](../troubleshoot.md#registry-push-and-authentication-failures) to diagnose one.

| Registry | Pre-creation | Notes |
| --- | --- | --- |
| Amazon ECR | Required | Every repository must exist before push. Registry-level auto-create policies exist but are off by default. |
| Docker Hub | Not required | Repositories auto-create in your user or organization namespace. Repository-count and pull rate limits apply. |
| GitHub Container Registry | Not required | Auto-creates under the user or organization namespace; visibility inherits from the organization's package settings. |
| Google Artifact Registry | Partial | Create the repository with `gcloud artifacts repositories create`; image paths inside it auto-create. |
| Google Container Registry | Not required | Auto-creates on push. Being phased out — target Artifact Registry for new deployments. |
| Azure Container Registry | Partial | The ACR instance must exist; image paths inside it auto-create. Wave needs the `AcrPush` role. |
| Harbor | Partial | Create the project through the UI or API; images inside it auto-create if project policy permits. |

## AWS cross-account role chaining

To access ECR registries in customer AWS accounts using IAM role credentials from the Seqera Platform, configure an intermediate "jump role" for cross-account access. Wave first assumes the jump role using its own credentials, then uses the jump role's temporary credentials to assume the target role from the Seqera Platform.

This is a two-hop role chaining pattern:

1. Wave assumes the jump role (using its default credentials).
2. Wave uses the jump role's temporary credentials to assume the customer's target role.
3. The target role's temporary credentials authenticate with ECR.

Configure jump role chaining with the following options:

`wave.aws.jump-role-arn` *(optional)*
: ARN of the intermediate IAM role that Wave assumes before assuming the target role from the Seqera Platform.
  Can be set using the `WAVE_AWS_JUMP_ROLE_ARN` environment variable.
  For example, `arn:aws:iam::123456789012:role/wave-jump-role`.

`wave.aws.jump-external-id` *(optional)*
: External ID used when assuming the jump role, for confused deputy protection.
  Can be set using the `WAVE_AWS_JUMP_EXTERNAL_ID` environment variable.

<div style={{marginLeft: '2em'}}>

:::note
When the jump role is not configured, Wave assumes target roles directly using its default credentials. The jump role is only used for role-based ECR authentication, not for static AWS credential flows.
:::

</div>

### STS retry configuration

Configure retry behavior for AWS STS AssumeRole calls:

`wave.aws.sts.retry.delay` *(optional)*
: Initial delay between retry attempts (default: `1s`).

`wave.aws.sts.retry.max-delay` *(optional)*
: Maximum delay between retry attempts (default: `10s`).

`wave.aws.sts.retry.attempts` *(optional)*
: Maximum number of retry attempts (default: `3`).

`wave.aws.sts.retry.multiplier` *(optional)*
: Exponential backoff multiplier (default: `2.0`).

`wave.aws.sts.retry.jitter` *(optional)*
: Jitter factor for retry delays (default: `0.25`).

### Jump role cache configuration

Configure caching for jump role temporary credentials to avoid redundant STS calls:

`wave.aws.jump-role-cache.duration` *(optional)*
: Maximum cache duration for jump role credentials (default: `45m`). Actual TTL is dynamically computed based on credential expiration with a 5-minute refresh buffer.

`wave.aws.jump-role-cache.max-size` *(optional)*
: Maximum number of cached jump role credential entries (default: `100`).

## HTTP client

Configure the HTTP client with the following options.

`wave.httpclient.connect-timeout` *(optional)*
: Connection timeout for the HTTP client (default: `20s`).

`wave.httpclient.retry.attempts` *(optional)*
: Number of HTTP client retry attempts (default: `5`).

`wave.httpclient.retry.delay` *(optional)*
: Delay between HTTP client retries (default: `500ms`).

`wave.httpclient.retry.jitter` *(optional)*
: Jitter factor for HTTP client retries (default: `0.25`).

`wave.httpclient.retry.max-delay` *(optional)*
: Maximum delay between HTTP client retries.

`wave.httpclient.retry.multiplier` *(optional)*
: Multiplier for HTTP client retries (default: `1.75`).

## Container build process

Configure how Wave builds container images and manages build logs.

`wave.build.buildkit-image` *(optional)*
: [Buildkit](https://github.com/moby/buildkit) container image used in the Wave build process (default: `public.cr.seqera.io/wave/buildkit:v0.25.2-rootless`).

`wave.build.cache` *(optional)*
: Cache repository for images built by Wave. Supports both container registry paths and S3 bucket paths.
  For example:

  <div style={{marginLeft: '2em'}}>

  - Container registry: `registry.example.com/wave/cache`
  - S3 bucket: `s3://my-bucket/wave/cache`

  </div>

`wave.build.cache-bucket-region` *(optional)*
: AWS region for the S3 cache bucket when using an S3 path in `wave.build.cache`.
  If not specified, Wave uses the `AWS_REGION` or `AWS_DEFAULT_REGION` environment variable.
  For example, `us-east-1`.
  This setting is only used when `wave.build.cache` is configured with an S3 bucket path.

`wave.build.cache-bucket-upload-parallelism` *(optional)*
: Number of layers uploaded to S3 in parallel during cache export.
  Each individual layer is uploaded with 5 threads using the AWS SDK Upload Manager.
  If not specified, BuildKit uses its default parallelism behavior.
  For example, `8`.
  This setting is only used when `wave.build.cache` is configured with an S3 bucket path.

`wave.build.compression` *(optional)*
: Compression type applied to cache layers (default: `gzip`).
  Options include: `uncompressed`, `estargz`, and `zstd`.

`wave.build.force-compression` *(optional)*
: When `true`, forces compression for each cache layer produced by the build process (default: `false`).

`wave.build.oci-mediatypes` *(optional)*
: When `true`, includes OCI media types in exported manifests (default: `true`).

`wave.build.public-repo` *(optional)*
: Public repository for container images built by Wave. Wave uses this repository as the build target when a freeze mode build is requested with Conda or pip packages via the v2 API and no `buildRepository` is provided. Images stored here default to the `imageSuffix` naming strategy. The registry hostname must be unique so that Wave can resolve default credentials correctly (it cannot be shared with `wave.build.repo` or `wave.build.cache`). If not set, you must specify a `buildRepository` in each freeze mode operation with packages.

`wave.build.repo` *(required when builds are enabled)*
: Docker container repository for container images built by Wave.

`wave.build.singularity-image` *(optional)*
: [Singularity](https://quay.io/repository/singularity/singularity?tab=tags) image used in the build process (default: `public.cr.seqera.io/wave/singularity:v4.2.1-r4`).

`wave.build.status.delay` *(optional)*
: Delay between build status checks (default: `5s`).

`wave.build.status.duration` *(optional)*
: Duration for build status checks (default: `90m`).

`wave.build.timeout` *(optional)*
: Maximum duration for the build process (default: `900s`).
  Keep `micronaut.server.idle-timeout` (default: `910s`) equal to or longer than this value.

`wave.build.trusted-timeout` *(optional)*
: Maximum duration for the build process when you are authenticated and freeze mode is enabled (default: `10m`).
  If set to less than `wave.build.timeout`, the longer `wave.build.timeout` value is used.

`wave.build.workspace` *(required when builds are enabled)*
: Path to the directory used by Wave to store artifacts such as Containerfiles, Trivy cache for scan, Buildkit context, and authentication configuration files.
  For example, `/efs/wave/build`.

:::tip
For S3 cache authentication setup, see [Build layer cache](configure-wave.md#build-layer-cache).
:::

### Cleanup

Wave deletes build job resources and work directories after builds complete. Configure the cleanup behavior with the following options.

`wave.cleanup.strategy` *(optional)*
: Cleanup strategy for build resources.
  Options include: `always`, `never`, and `onsuccess` (clean up only when the build succeeds).
  When unset, Wave cleans up unless `wave.debug` is `true`.

`wave.cleanup.succeeded` *(optional)*
: How long the job resources and work directory of a successful build are retained before deletion (default: `30m`).

`wave.cleanup.failed` *(optional)*
: How long the job resources and work directory of a failed build are retained before deletion (default: `1d`).

`wave.cleanup.range` *(optional)*
: Maximum number of expired entries processed in each cleanup run (default: `200`).

`wave.cleanup.startup-delay` *(optional)*
: Delay before the cleanup service starts after boot. The actual delay is randomized around this value (default: `10s`).

`wave.cleanup.run-interval` *(optional)*
: Interval between cleanup runs (default: `30s`).

### Build process logs

Configure how Wave stores and delivers build logs from containers and Kubernetes pods. You can retrieve these logs later or include them in build completion emails.

`wave.build.locks.path` *(required when builds are enabled)*
: Path where Wave stores Conda lock files. Can be an S3 URI (for example, `s3://my-bucket/wave/locks`) or a local filesystem path.

`wave.build.logs.max-length` *(optional)*
: Maximum number of bytes read from a log file. If a log file exceeds this limit, it is truncated (default: `100000` (100 KB)).

`wave.build.logs.path` *(required when builds are enabled)*
: Path where Wave stores build logs. Can be an S3 URI (for example, `s3://my-bucket/wave/logs`) or a local filesystem path. When using an S3 URI, Wave automatically extracts the key prefix for log file organization.

### Kubernetes container build process

Configure Kubernetes-specific settings for Wave. Build and scan processes share most configurations except for CPU and memory requirements.

`wave.build.k8s.labels` *(optional)*
: Labels for Wave build Kubernetes pods.

`wave.build.k8s.namespace` *(required for Kubernetes builds)*
: Kubernetes namespace where Wave runs build pods.

`wave.build.k8s.dns.policy` *(optional)*
: DNS policy for Wave build Kubernetes pods. For example, `None`, `Default`, `ClusterFirst`.
  When set to `None`, you must also configure `wave.build.k8s.dns.servers`.

`wave.build.k8s.dns.servers` *(optional)*
: Custom DNS server IP addresses for Wave build pods. Used when `wave.build.k8s.dns.policy` is set to `None`.
  For example, `['1.1.1.1', '8.8.8.8']`.

`wave.build.k8s.node-selector` *(optional)*
: Node selector for Wave build Kubernetes pods. Value is a map where keys are platform identifiers
  and values are Kubernetes node label selectors in `label=value` format.
  Supported platform keys: `linux/amd64`, `linux/arm64`, `noarch`.
  For example:

  <div style={{marginLeft: '2em'}}>

  ```yaml
  wave.build.k8s.node-selector:
    linux/amd64: 'seqera.io/wave-build-amd64=true'
    linux/arm64: 'seqera.io/wave-build-arm64=true'
    noarch: 'seqera.io/wave-build=true'
  ```

  </div>

`wave.build.k8s.resources.requests.cpu` *(optional)*
: [CPU resources](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#resource-units-in-kubernetes) allocated to Wave build processes.
  For example, set to `2` (2 CPU cores) or `1500Mi` (1.5 CPU cores).

`wave.build.k8s.resources.requests.memory` *(optional)*
: [Memory resources](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#resource-units-in-kubernetes) allocated to Wave build processes.
  For example, set to `3Gi` (3 Gigabytes) or `2000Mi` (2000 Megabytes).

`wave.build.k8s.service-account` *(optional)*
: Kubernetes service account name for Wave build pods.

`wave.build.k8s.storage.claim-name` *(optional)*
: Volume claim name for Wave build Kubernetes pods.

`wave.build.k8s.storage.mount-path` *(optional)*
: Volume mount path for Wave build Kubernetes pods.

## Container scan process

Configure Wave's vulnerability scanning process, which uses a [Trivy Docker image](https://hub.docker.com/r/aquasec/trivy) with customizable tags and severity levels.

`wave.scan.image.name`  *(optional)*
: Container image used for security scanning (default: `public.cr.seqera.io/wave/scanner:v1-0.65.0-oras-1.3.0`).

`wave.scan.reports.path` *(required when scanning is enabled)*
: S3 bucket path where Wave stores SBOM reports.
  For example, `s3://wave-store/scan-reports`.

`wave.scan.severity` *(optional)*
: [Severity levels](https://aquasecurity.github.io/trivy/v0.22.0/vulnerability/examples/filter/) to report in vulnerability scanning.
: Options include: `MEDIUM`, `HIGH`, and `CRITICAL`.

`wave.scan.status.duration` *(optional)*
: Duration for which scan status records are retained (default: `5d`).

### Kubernetes Wave scan process

Configure Wave scan process resource requirements for Kubernetes deployments.

`wave.scan.k8s.resources.requests.cpu` *(optional)*
: [CPU resources](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#resource-units-in-kubernetes) allocated to Wave scan processes.
  For example, set to `2` (2 CPU cores) or `1500Mi` (1.5 CPU cores).

`wave.scan.k8s.resources.requests.memory` *(optional)*
: [Memory resources](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#resource-units-in-kubernetes) allocated to Wave scan processes.
  For example, set to `3Gi` (3 Gigabytes) or `2000Mi` (2000 Megabytes).

## Container mirror process

Configure Kubernetes resource requirements for Wave's container mirroring operations.

`wave.mirror.k8s.resources.requests.cpu` *(optional)*
: CPU resources requested for mirror Kubernetes pods.

`wave.mirror.k8s.resources.requests.memory` *(optional)*
: Memory resources requested for mirror Kubernetes pods.

`wave.mirror.k8s.resources.limits.cpu` *(optional)*
: CPU resource limit for mirror Kubernetes pods.

`wave.mirror.k8s.resources.limits.memory` *(optional)*
: Memory resource limit for mirror Kubernetes pods.

## Proxy cache

Configure Wave's in-memory proxy cache for registry responses.

`wave.proxy-cache.enabled` *(optional)*
: When `true`, activates the proxy cache (default: `false`).

`wave.proxy-cache.duration` *(optional)*
: Cache entry expiration duration (default: `120s`).

`wave.proxy-cache.max-size` *(optional)*
: Maximum number of entries in the proxy cache (default: `10000`).

## Rate limits

Configure rate limits for anonymous and authenticated user access.

:::note
These options take effect only when the `rate-limit` entry is included in the `MICRONAUT_ENVIRONMENTS` variable of your deployment.
:::

`rate-limit.build.anonymous` *(optional)*
: Rate limit for build requests from anonymous users (default: `10/1h`).

`rate-limit.build.authenticated` *(optional)*
: Rate limit for build requests from authenticated users (default: `10/1m`).

`rate-limit.pull.anonymous` *(optional)*
: Rate limit for pull requests from anonymous users (default: `100/1h`).

`rate-limit.pull.authenticated` *(optional)*
: Rate limit for pull requests from authenticated users (default: `100/1m`).

`rate-limit.timeout-errors.max-rate` *(optional)*
: Maximum rate of timeout errors before Wave begins rejecting requests (default: `20/2m`).

## Security headers

Wave sends HTTP security headers on all responses by default. Configure them with the following options:

`wave.security.http-headers.enabled` *(optional)*
: When `true`, Wave adds security headers to HTTP responses (default: `true`).

`wave.security.http-headers.hsts.max-age` *(optional)*
: `Strict-Transport-Security` max age in seconds (default: `31536000`).

`wave.security.http-headers.hsts.include-sub-domains` *(optional)*
: When `true`, applies HSTS to subdomains (default: `true`).

`wave.security.http-headers.frame-options` *(optional)*
: `X-Frame-Options` header value (default: `DENY`).

`wave.security.http-headers.content-type-options` *(optional)*
: `X-Content-Type-Options` header value (default: `nosniff`).

`wave.security.http-headers.referrer-policy` *(optional)*
: `Referrer-Policy` header value (default: `strict-origin-when-cross-origin`).

`wave.security.http-headers.permissions-policy` *(optional)*
: `Permissions-Policy` header value (default: `camera=(), microphone=(), geolocation=()`).

`wave.security.http-headers.content-security-policy` *(optional)*
: `Content-Security-Policy` header value. Adjust this if you front Wave with additional origins (default: `default-src 'self'; style-src 'self' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; img-src 'self' data:; frame-ancestors 'none'`).

## Database and cache

Wave uses external database and caching services to store application data and improve performance.

### Redis

Configure Redis with the following options.

`redis.client.timeout` *(optional)*
: Timeout duration (in milliseconds) for Redis client operations (default: `5000` (5 seconds)).

`redis.password` *(optional)*
: Password used to authenticate with the Redis server.
  Can be set using the `${REDIS_PASSWORD}` environment variable.

`redis.pool.enabled` *(optional)*
: When `true`, activates the Redis connection pool (default: `true`).

`redis.pool.maxIdle` *(optional)*
: Maximum number of idle connections in the Redis connection pool (default: `10`).

`redis.pool.maxTotal` *(optional)*
: Maximum number of connections in the Redis connection pool (default: `50`).

`redis.pool.minIdle` *(optional)*
: Minimum number of idle connections in the Redis connection pool (default: `0`).

`redis.uri` *(required)*
: URI for connecting to Redis (default format: `redis://${REDIS_HOST:redis}:${REDIS_PORT:6379}`).
  Can be set using the `${REDIS_URI}` environment variable.

### PostgreSQL

Configure PostgreSQL with the following options.

`wave.db.password` *(required)*
: Password for the PostgreSQL database user.
  Can be set using the `${WAVE_DB_PASSWORD}` environment variable.

`wave.db.uri` *(required)*
: JDBC connection string for the PostgreSQL database.
  For example, `jdbc:postgresql://localhost:5432/wave`.
  Can be set using the `${WAVE_DB_URI}` environment variable.

`wave.db.user` *(required)*
: Username for authenticating with the PostgreSQL database.
  Can be set using the `${WAVE_DB_USER}` environment variable.

## Blob cache

Configure how Wave caches container blobs to improve client performance. Wave can also delegate transfer tasks to Kubernetes pods for scalability.

`wave.blob-cache.base-url` *(optional)*
: URL that overrides the base URL (the part before the blob path) of blobs sent to the client.

`wave.blob-cache.cloudflare.lifetime` *(optional)*
: Validity duration of the Cloudflare WAF token.

`wave.blob-cache.cloudflare.secret-key` *(optional)*
: [Cloudflare secret](https://developers.cloudflare.com/waf/custom-rules/use-cases/configure-token-authentication/) used to create the WAF token.

`wave.blob-cache.url-signature-duration` *(optional)*
: Validity duration of the AWS S3 URL signature (default: `30m`).

`wave.blob-cache.enabled` *(optional)*
: When `true`, activates the blob cache (default: `false`).

`wave.blob-cache.k8s.resources.requests.cpu` *(optional)*
: [CPU resources](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#resource-units-in-kubernetes) requested for the Kubernetes pod used for blob binary transfers.

`wave.blob-cache.k8s.resources.requests.memory` *(optional)*
: [Memory resources](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#resource-units-in-kubernetes) requested for the Kubernetes pod used for blob binary transfers.

`wave.blob-cache.k8s.resources.limits.cpu` *(optional)*
: CPU resource [limit](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#resource-units-in-kubernetes) for the Kubernetes pod used for blob binary transfers.

`wave.blob-cache.k8s.resources.limits.memory` *(optional)*
: Memory resource [limit](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#resource-units-in-kubernetes) for the Kubernetes pod used for blob binary transfers.

`wave.blob-cache.s5cmd-image` *(optional)*
: Container image that supplies the [s5cmd tool](https://github.com/peak/s5cmd) for uploading blob binaries to the S3 bucket (default: `public.cr.seqera.io/wave/s5cmd:v2.3.0`).

`wave.blob-cache.signing-strategy` *(optional)*
: URL signing strategy for different services.
  Currently supports AWS S3 and Cloudflare service.
  Options include: `aws-presigned-url` and `cloudflare-waf-token`.

`wave.blob-cache.status.delay` *(optional)*
: Delay between status checks for blob binary transfers from the repository to the cache (default: `2s`).

`wave.blob-cache.status.duration` *(optional)*
: Duration for which blob transfer status records are retained in cache (default: `1h`).

`wave.blob-cache.storage.access-key` *(required when blob cache is enabled)*
: Access key credential for the caching service.

`wave.blob-cache.storage.bucket` *(required when blob cache is enabled)*
: Name of the Cloudflare or S3 bucket.
  For example, `s3://wave-blob-cache`.

`wave.blob-cache.storage.endpoint` *(optional)*
: Storage endpoint URL for blob binary downloads and uploads.

`wave.blob-cache.storage.region` *(required when blob cache is enabled)*
: AWS region of the bucket.

`wave.blob-cache.storage.secret-key` *(required when blob cache is enabled)*
: Secret key credential for the caching service.

<div style={{marginLeft: '2em'}}>

:::note
Static credentials (`access-key` and `secret-key`) are currently required for blob cache storage access. IAM-based authentication (such as EKS Pod Identity or IRSA) is not yet supported for the blob cache feature. This differs from the S3 build cache, which does support IAM-based authentication.
:::

</div>

`wave.blob-cache.timeout` *(optional)*
: Timeout for blob binary transfers. Transfers exceeding this duration fail (default: `10m`).

## Email configuration

Configure how Wave sends email notifications.

Email delivery requires `mail` in `MICRONAUT_ENVIRONMENTS`. Add `aws-ses` as well to send through Amazon SES with IAM authentication, in which case only `mail.from` applies and the `mail.smtp.*` settings are ignored.

`mail.from` *(required when mail is enabled)*
: Sender email address for Wave notifications.
  Can be set using the `${MAIL_FROM}` environment variable.

`mail.smtp.host` *(required when SMTP is used)*
: SMTP server hostname.

`mail.smtp.port` *(required when SMTP is used)*
: SMTP server port, typically `587` for STARTTLS or `465` for implicit TLS.

`mail.smtp.user` *(optional)*
: Username for SMTP authentication.

`mail.smtp.password` *(optional)*
: Password for SMTP authentication.

`mail.smtp.auth` *(optional)*
: When `true`, authenticate to the SMTP server.

`mail.smtp.starttls.enable` *(optional)*
: When `true`, upgrade the connection with STARTTLS.

`mail.smtp.starttls.required` *(optional)*
: When `true`, fail rather than fall back to an unencrypted connection.

`mail.smtp.ssl.protocols` *(optional)*
: Space-separated list of permitted TLS protocols, for example `TLSv1.2 TLSv1.3`.

## Metrics

Configure the Wave Metrics service, which provides data about container builds and pulls per organization and date.

`wave.metrics.enabled` *(optional)*
: When `true`, activates Wave metrics (default: `false`).

## Accounts

Configure user credentials for accessing authenticated Wave APIs and services.

`wave.accounts` *(optional)*
: Credentials for accessing authenticated Wave APIs such as the metrics API.
  A map of usernames to SHA-256 hex checksums of the corresponding passwords:

  <div style={{marginLeft: '2em'}}>

  ```yaml
  wave:
    accounts:
      # SHA-256 checksum of the password 'bar'
      foo: "fcde2b2edba56bf408601fb721fe9b5c338d10ee429ea04fae5511b68fbf8fb9"
  ```

  </div>

## License server

Configure the connection to the Seqera license management server:

`license.server.url` *(optional)*
: URL of the Seqera license management server.
  Required when license validation is enabled.
  For example, `https://licenses.seqera.io`.

## Job manager

Configure polling and scheduling intervals for Wave's asynchronous job processing.

`wave.job-manager.max-running-jobs` *(optional)*
: Maximum number of build, scan, and mirror jobs Wave runs concurrently (default: `20`).
  Pair this with a `ResourceQuota` on the build namespace to bound build resource usage.

`wave.job-manager.poll-interval` *(optional)*
: Polling interval for checking job status (default: `1s`).

`wave.job-manager.scheduler-interval` *(optional)*
: Interval for the job scheduler to process queued jobs (default: `1s`).

## Message stream

Configure how Wave consumes messages from the Redis stream used for internal event processing.

`wave.message-stream.claim-timeout` *(optional)*
: Timeout for claiming messages from the Redis stream (default: `5s`).

`wave.message-stream.consume-warn-timeout` *(optional)*
: Threshold duration after which a slow message consumer triggers a warning (default: `4s`).

## Thread monitor

Configure thread monitoring to detect and diagnose thread exhaustion issues. When enabled, Wave writes a thread dump to disk if the active thread count exceeds the configured threshold.

`wave.thread-monitor.dump-file` *(optional)*
: File path where thread dumps are written when the thread count exceeds the threshold.
  For example, `/efs/wave/dump/threads-dump.txt`.

`wave.thread-monitor.dump-threshold` *(optional)*
: Thread count threshold that triggers a thread dump (default: `200`).

`wave.thread-monitor.interval` *(optional)*
: Interval for the thread monitor check (default: `5m`).

## Trace

Configure slow endpoint logging to identify HTTP endpoints that exceed expected response times.

`wave.trace.slow-endpoint.duration` *(optional)*
: Threshold duration for logging slow HTTP endpoints (default: `1m`).

## Logging

Configure the Logback STDOUT log pattern per deployment. Useful when the runtime environment (for example, a log collector) already prefixes each line with a timestamp and you want to omit Wave's own date prefix.

`WAVE_LOG_PATTERN` *(optional, environment variable only)*
: Logback pattern used by the STDOUT appender (default: `%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg >> wt=%X{requestId}%n`).
  To omit the date/time prefix, set the variable to a pattern without the leading `%d{...}` token, for example:

  ```
  WAVE_LOG_PATTERN=[%thread] %-5level %logger{36} - %msg >> wt=%X{requestId}%n
  ```
