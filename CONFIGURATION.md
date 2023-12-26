# Wave Application Configurations

| Variable                      | Description                            | Default Value        | Optional |
|-------------------------------|----------------------------------------|----------------------|----------|
| `micronaut.application.name`| The name of wave application.          | `wave-app`           | true     |
| `micronaut.server.port`      | The port for wave server.              | `8080`               | true     |
| `wave.allowAnonymous`         | Allow anonymous access to wave server. | `false`              | false    |
| `wave.server.url`             | URL for the Wave server.               | `${WAVE_SERVER_URL}` | true     |
| `wave.tokens.cache.duration`          | Duration for caching tokens.           |                      | true     |
| `tower.endpoint.url`                 | URL for the seqera platform API.       |                      | true     |
| `logger.levels.io.seqera`     | Log level for `io.seqera` package.     | `TRACE`              | true     |

# Wave Docker Registries Configurations (note: you will find them in config.yml in the root directory of the project)

| Variable                                     | Description                                   | Default Value   | Optional |
|----------------------------------------------|-----------------------------------------------|-----------------|----------|
| `wave.arch`                                  | Architecture for Wave.                        | `"x86_64"`      | false    |
| `wave.registries.default`                    | Default Docker registry for Wave.             | `"docker.io"`   | false    |
| `wave.registries.docker.io.username`         | Docker Hub username for authentication.       | `"${DOCKER_USER}"` | true     |
| `wave.registries.docker.io.password`         | Docker Hub password or PAT for authentication.| `"${DOCKER_PAT}"` | true     |
| `wave.registries.quay.io.username`           | Quay.io username for authentication.         | `"${QUAY_USER}"` | true     |
| `wave.registries.quay.io.password`           | Quay.io password or PAT for authentication.  | `"${QUAY_PAT}"` | true     |
| `wave.registries.195996028523.dkr.ecr.eu-west-1.amazonaws.com.username` | AWS ECR username for authentication. | `"${AWS_ACCESS_KEY_ID}"` | true     |
| `wave.registries.195996028523.dkr.ecr.eu-west-1.amazonaws.com.password` | AWS ECR password for authentication. | `"${AWS_SECRET_ACCESS_KEY}"` | true     |
| `wave.registries.seqeralabs.azurecr.io.username` | Azure Container Registry username for authentication. | `"${AZURECR_USER}"` | true     |
| `wave.registries.seqeralabs.azurecr.io.password` | Azure Container Registry password or PAT for authentication. | `"${AZURECR_PAT}"` | true     |

# Wave Http client Configurations

| Variable                      | Description                                      | Default Value | Optional |
|-------------------------------|--------------------------------------------------|---------------|----------|
| `wave.httpclient.connectTimeout` | connection timeout for HTTP client.              | `20s`         | false    |
| `wave.httpclient.retry.delay` | Delay for HTTP client retries.                   | `1s`          | false    |
| `wave.httpclient.retry.attempts` | Number of HTTP client retry attempts.            | `5`           | false    |
| `wave.httpclient.retry.maxDelay` | Maximum delay for HTTP client retries.           |               | true     |
| `wave.httpclient.retry.jitter` | Jitter fot HTTP client retries.                  | `0.25`        | false    |
| `wave.httpclient.retry.multiplier` | Multiplier for HTTP client retries.              | `1.0`         | false    |
| `micronaut.http.services.stream-client.read-timeout` | Read timeout for the streaming HTTP client.      | `30s`                                | false    |
| `micronaut.http.services.stream-client.read-idle-timeout` | Read idle timeout for the streaming HTTP client. | `120s`                           | false    |

# Wave build process Configurations

| Variable                      | Description                                                          | Default Value                                        | Optional |
|-------------------------------|----------------------------------------------------------------------|------------------------------------------------------|----------|
| `wave.build.timeout`                  | Timeout for the build process.                                       | `5m `                                                | false    |
| `wave.build.workspace`                | Path to workspace for the build process. e.g. /efs/wave/build        |                                                      | false    |
| `wave.build.cleanup`                  | Cleanup strategy after the build process. Options: `"OnSuccess"`.    |                                                      | true     |
| `wave.build.kaniko-image`     | Docker image for Kaniko for build process.                           | `gcr.io/kaniko-project/executor:v1.19.2`             | false    |
| `wave.build.singularity-image` | Singularity image for the build process.                             | `quay.io/singularity/singularity:v3.11.4-slim`       | false    |
| `wave.build.singularity-image-arm64` | Singularity ARM64 image for the build process.                       | `quay.io/singularity/singularity:v3.11.4-slim-arm64` | true     |
| `wave.build.repo`             | Docker container repository for the docker images build by wave.     |                                                      | false    |
| `wave.build.cache`            | Docker container repository to cache layers of images build by wave. |                                                      | false    |
| `wave.build.status.delay`     | Delay for build status checks.                                       | `5s`                                                 | false    |
| `wave.build.status.duration`  | Duration for build status checks.                                    | `1d`                                                 | false    |
| `wave.build.public`                  | Deafult public re[ository for wave.                                  |                                                      | true     |
| `wave.build.compress-caching`                  | wave caching compression for build process.                          | true                                                 | false    |

## Spack  Configurations for wave build process(Note: these configuration are mandatory if you want to support spack in you wave installation)

| Variable                                   | Description                                              | Default Value                         | Optional |
|--------------------------------------------|----------------------------------------------------------|---------------------------------------|----------|
| `wave.build.spack.cacheDirectory`     | Cache directory for Spack. e.g. `/efs/wave/spack/cache`              |                                                      | false    |
| `wave.build.spack.cacheMountPath`     | Cache mount path for Spack. e.g. `/var/seqera/spack/cache`           |                                                      | false    |
| `wave.build.spack.secretKeyFile`      | Secret key file for Spack. e.g. `/efs/wave/spack/key`                |                                                      | false    |
| `wave.build.spack.secretMountPath`    | Secret mount path for Spack. e.g. `/var/seqera/spack/key`            |                                                      | false    |

# Kubernetes Configurations for Wave build process (Note: If you will use kubernetes)

| Variable                                   | Description                                              | Default Value                         | Optional |
|--------------------------------------------|----------------------------------------------------------|---------------------------------------|----------|
| `wave.build.k8s.namespace`                 | Kubernetes namespace for wave.                           | | false    |
| `wave.build.k8s.storage.claimName`         | Claim name for wave kubernetes pod storage.              |     | true     |
| `wave.build.k8s.storage.mountPath`         | Mount path for wave kubernetes pod storage.              |        | true     |
| `wave.build.k8s.labels`                    | Labels wave kubernetes pod.                              |  | true     |
| `wave.build.k8s.node-selector`             | Node selector for wave kubernetes pod.                   |  | true     |
| `wave.build.k8s.service-account`           | Service account name for wve kubernetes cluster.         |  | true     |
| `wave.build.k8s.resources.requests.cpu`    | allocate number of CPUs for build process in kubernetes. |                        | true     |
| `wave.build.k8s.resources.requests.memory` | allocate memory for build process in kubernetes. e.g. `2000Mi`           |                        | true     |

# Wave scan process Configurations

| Variable                      | Description                                                            | Default Value          | Optional |
|-------------------------------|------------------------------------------------------------------------|------------------------|----------|
| `wave.scan.enabled`                   | Enable or disable vulnerability scanning.                              | `false`                | false    |
| `wave.scan.severity`                  | Severity level for vulnerability scanning. e.g. "MEDIUM,HIGH,CRITICAL" |                        | true     |
| `wave.scan.image.name`        | Docker Image used for container securioty scanning.                    | `aquasec/trivy:0.47.0` | false    |

# Kubernetes Configurations for Wave build process (Note: If you will use kubernetes)

| Variable                      | Description                                                      | Default Value                             | Optional |
|-------------------------------|------------------------------------------------------------------|-------------------------------------------|----------|
| `wave.scan.k8s.resources.requests.cpu`        | allocate number of CPUs for scaning process in kubernetes        |                        | true     |
| `wave.scan.k8s.resources.requests.memory`        | allocate memory for scaning process in kubernetes. e.g. `1000Mi` |                        | true     |

# Wave Additional Configurations (Note: If you enable rate limiting in wave by adding 'rate-limit' in micronaut environment)

| Variable                      | Description                                         | Default Value                     | Optional |
|-------------------------------|-----------------------------------------------------|-----------------------------------|----------|
| `rate-limit.build.anonymous`          | Rate limit configuration for anonymous build requests.            |      `10/1h`   | false    |
| `rate-limit.build.authenticated`      | Rate limit configuration for authenticated build requests.        |    `10/1m`    | false    |
| `rate-limit.pull.anonymous`           | Rate limit configuration for anonymous pull requests.             |      `100/1h`  | false    |
| `rate-limit.pull.authenticated`       | Rate limit configuration for authenticated pull requests.         | `100/1m`      | false    |

# Wave cache and database Configurations

| Variable             | Description                  | Default Value                                    | Optional |
|----------------------|------------------------------|--------------------------------------------------|----------|
| `redis.uri`          | URI for connecting to Redis. | `redis://${REDIS_HOST:redis}:${REDIS_PORT:6379}` | false    |
| `redis.pool.enabled` | enable redis pool.           | true                                             | true     |
| `surrealdb.ns`       | Surreal database namespace.  | `${SURREALDB_NS}`                                | false    |
| `surrealdb.db`       | Surreal database name.       | `${SURREALDB_DB}`                                | false    |
| `surrealdb.url`      | Surreal database url.        | `${SURREALDB_URL}`                               | false    |
| `surrealdb.user`     | Surreal database username.   | `${SURREALDB_USER}`                              | false    |
| `surrealdb.password` | Surreal databse password.    | `${SURREALDB_PASSWORD`                               | false    |
| `surrealdb.init-db`  | should initiat surreal DB.   |                           | true     |

# Wave Mail Configurations

| Variable                      | Description                                         | Default Value                             | Optional |
|-------------------------------|-----------------------------------------------------|-------------------------------------------|----------|
| `mail.from`                          | Email address for sending mail from wave.                         |       | false    |

# Wave build logs Configuration

| Variable                      | Description                                              | Default Value                             | Optional |
|-------------------------------|----------------------------------------------------------|-------------------------------------------|----------|
| `logger.levels.io.micronaut.retry.intercept.RecoveryInterceptor` | Log level for `RecoveryInterceptor`.                     | `OFF` | false    |
| `micronaut.object-storage.aws.build-logs.bucket` | AWS S3 bucket, where wave will store build process logs. | `${wave.build.logs.bucket}`         | false    |


# Jackson Configurations

| Variable                      | Description                                         | Default Value                             | Optional |
|-------------------------------|-----------------------------------------------------|-------------------------------------------|----------|
| `jackson.serialization.writeDatesAsTimestamps` | Write dates as timetstamps in jackson serialization | `false`                                  | false    |

# Micronaut specific Configuration

| Variable                    | Description                                                                                 | Default Value                             | Optional |
|-----------------------------|---------------------------------------------------------------------------------------------|-------------------------------------------|----------|
| `micronaut.caches.cache-20sec.expire-after-access` | Cache with a 20-second expire-after-access policy.                                          | `20s`                                     | false    |
| `micronaut.caches.cache-1min.expire-after-access`  | Cache with a 1-minute expire-after-access policy.                                           | `1m`                                      | false    |
| `micronaut.server.thread-selection` | Thread selection strategy.                                                                  | `AUTO`                                    | false    |
| `micronaut.server.idle-timeout` | Idle timeout for the server. note this should be >= the build timeout (which is 15 minutes) | `910s`                     | false    |
| `micronaut.views.folder`       | Folder for Micronaut views.                                                                 | `io/seqera/wave`                          | false    |
| `micronaut.router.static-resources.css.paths` | CSS static resource paths.                                                                  | `classpath:io/seqera/wave/css`           | false    |
| `micronaut.router.static-resources.css.mapping` | Mapping for CSS resources.                                                                  | `/css/**`                                | false    |
| `micronaut.router.static-resources.css.enabled` | Enable or disable CSS resources.                                                            | `true`                                   | false    |
| `micronaut.router.static-resources.assets.paths` | Assets static resource paths.                                                               | `classpath:io/seqera/wave/assets`        | false    |
| `micronaut.router.static-resources.assets.mapping` | Mapping for assets resources.                                                               | `/assets/**`                             | false    |
| `micronaut.router.static-resources.assets.enabled` | Enable or disable assets resources.                                                         | `true`                                   | false    |
| `micronaut.executors.stream-executor.type` | Executor type for the stream executor.                                                      | `FIXED`                                  | false    |
| `micronaut.executors.stream-executor.number-of-threads` | Number of threads for the stream executor.                                                  | `16`                     | false    |
| `micronaut.netty.event-loops.stream-pool.executor` | Executor for the stream pool event loops.                                                   | `stream-executor`                  | false    |
# Notes
- Customize these configurations based on your application's requirements.
- Refer to the official Micronaut documentation for more details on available configuration properties: [Micronaut Configuration Reference](https://docs.micronaut.io/latest/guide/index.html#config)
