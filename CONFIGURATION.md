# Wave Application Configuration

Set Wave configuration values using environment variables, or in config.yml configuration file

## General configuration

| Variable                      | Description                            | Default Value / Environment Variable | Optional |
|-------------------------------|----------------------------------------|-------------------------------------|----------|
| `micronaut.application.name`| The name of wave application.          | `wave-app`                          | true     |
| `micronaut.server.port`      | The port for wave server.              | `8080`                              | true     |
| `wave.allowAnonymous`         | Allow anonymous access to wave server. | `false`                             | false    |
| `wave.server.url`             | URL for the Wave server.               | `${WAVE_SERVER_URL}`                | true     |
| `wave.tokens.cache.duration`          | Duration for caching tokens.           |                                     | true     |
| `tower.endpoint.url`                 | URL for the seqera platform API.       |                                     | true     |
| `logger.levels.io.seqera`     | Log level for `io.seqera` package.     | `TRACE`                             | true     |


## Container registry configuration

**Note**: you will find them in `config.yml` in the root directory of the project. These configurations are important for the authentication of wave to the repositories used to push or pull artifacts by wave 

| Variable                                     | Description                                   | Default Value / Environment Variable   | Optional |
|----------------------------------------------|-----------------------------------------------|-----------------|----------|
| `wave.arch`                                  | Architecture for Wave.                        | `x86_64`      | false    |
| `wave.registries.default`                    | Default Docker registry for Wave.             | `docker.io`   | false    |
| `wave.registries.docker.io.username`         | Docker Hub username for authentication.       | `${DOCKER_USER}` | true     |
| `wave.registries.docker.io.password`         | Docker Hub password or PAT for authentication.| `${DOCKER_PAT}` | true     |
| `wave.registries.quay.io.username`           | Quay.io username for authentication.         | `${QUAY_USER}` | true     |
| `wave.registries.quay.io.password`           | Quay.io password or PAT for authentication.  | `${QUAY_PAT}` | true     |
| `wave.registries.195996028523.dkr.ecr.eu-west-1.amazonaws.com.username` | AWS ECR username for authentication. | `${AWS_ACCESS_KEY_ID}` | true     |
| `wave.registries.195996028523.dkr.ecr.eu-west-1.amazonaws.com.password` | AWS ECR password for authentication. | `${AWS_SECRET_ACCESS_KEY}` | true     |
| `wave.registries.seqeralabs.azurecr.io.username` | Azure Container Registry username for authentication. | `${AZURECR_USER}` | true     |
| `wave.registries.seqeralabs.azurecr.io.password` | Azure Container Registry password or PAT for authentication. | `${AZURECR_PAT}` | true     |


## HTTP client configuration

| Variable                      | Description                                      | Default Value / Environment Variable | Optional |
|-------------------------------|--------------------------------------------------|---------------|----------|
| `wave.httpclient.connectTimeout` | Connection timeout for http client.              | `20s`         | false    |
| `wave.httpclient.retry.delay` | Delay for http client retries.                   | `1s`          | false    |
| `wave.httpclient.retry.attempts` | Number of http client retry attempts.            | `5`           | false    |
| `wave.httpclient.retry.maxDelay` | Maximum delay for http client retries.           |               | true     |
| `wave.httpclient.retry.jitter` | Jitter fot http client retries.                  | `0.25`        | false    |
| `wave.httpclient.retry.multiplier` | Multiplier for http client retries.              | `1.0`         | false    |
| `micronaut.http.services.stream-client.read-timeout` | Read timeout for the streaming http client.      | `30s`                                | false    |
| `micronaut.http.services.stream-client.read-idle-timeout` | Read idle timeout for the streaming http client. | `120s`                           | false    |


## Container build process configuration

| Variable                      | Description                                                                                             | Default Value / Environment Variable                 | Optional |
|-------------------------------|---------------------------------------------------------------------------------------------------------|-----------------------------------------------------|----------|
| `wave.build.timeout`                  | Timeout for the build process.                                                                          | `5m`                                                | false    |
| `wave.build.workspace`                | Path to workspace for the build process. e.g. `/efs/wave/build`                                         |                                                     | false    |
| `wave.build.cleanup`                  | Cleanup strategy after the build process. Options: `OnSuccess`.                                         |                                                     | true     |
| `wave.build.kaniko-image`     | [Kaniko](https://github.com/GoogleContainerTools/kaniko) docker image to use in the wave build process. | `gcr.io/kaniko-project/executor:v1.19.2`            | false    |
| `wave.build.singularity-image` | Singularity image for the build process.                                                                | `quay.io/singularity/singularity:v3.11.4-slim`      | false    |
| `wave.build.singularity-image-arm64` | Singularity ARM64 image for the build process.                                                          | `quay.io/singularity/singularity:v3.11.4-slim-arm64` | true     |
| `wave.build.repo`             | Docker container repository for the docker images build by wave.                                        |                | false    |
| `wave.build.cache`            | Docker container repository to cache layers of images build by wave.                                    |               | false    |
| `wave.build.status.delay`     | Delay between build status checks.                                                                      | `5s`    | false    |
| `wave.build.status.duration`  | Duration for build status checks.                                                                       | `1d`              | false    |
| `wave.build.public`                  | Default public repository for wave.                                                                     |                | true     |
| `wave.build.compress-caching`                  | Whether to compress cache layers produced by the build process.                                                             | `true`     | false    |


### Spack configuration for wave build process

**Note**: these configuration are mandatory to support Spack in a wave installation.

| Variable                           | Description                                                                                                                                                                                    | Default Value  / Environment Variable | Optional |
|------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------|----------|
| `wave.build.spack.cacheDirectory`  | Path to the directory to cache spack packages. e.g. `/efs/wave/spack/cache`.                                                                                                                   |               | false    |
| `wave.build.spack.cacheMountPath`  | Path to local directory where to save spack build cache files, e.g. `/efs/wave/spack/cache`.                                                                                                   |                 | false    |
| `wave.build.spack.secretKeyFile`   | Path to file containing the PGP private key to [sign Spack packages built by wave](https://spack.readthedocs.io/en/latest/binary_caches.html#build-cache-signing), e.g. `/efs/wave/spack/key`. |                 | false    |
| `wave.build.spack.secretMountPath` | Path mounted inside spack docker image for PGP private key mentioned specified by `wave.build.spack.secretKeyFile`. e.g. `/var/seqera/spack/key`.                                              |                   | false    |
| `wave.build.spack.cacheBucket`     | S3 bucket for spack binary cache e.g. `s3://spack-binarycache`.                                                                                                                                |                   | true     |

### Build process logs configuration

| Variable                      | Description                                              | Default Value / Environment Variable | Optional |
|-------------------------------|----------------------------------------------------------|-------------------------------------------|----------|
| `logger.levels.io.micronaut.retry.intercept.RecoveryInterceptor` | Log level for `RecoveryInterceptor`.                     | `OFF` | false    |
| `micronaut.object-storage.aws.build-logs.bucket` | AWS S3 bucket, where wave will store build process logs. | `${wave.build.logs.bucket}`         | false    |


### Kubernetes configuration for container build process

**Note**: only applies when using Kubernetes.

| Variable                                   | Description                                              | Default Value / Environment Variable | Optional |
|--------------------------------------------|----------------------------------------------------------|---------------------------------------|----------|
| `wave.build.k8s.namespace`                 | Kubernetes namespace where to run wave build pods. | | false    |
| `wave.build.k8s.storage.claimName`         | Volume claim name for wave build kubernetes pods.              |     | true     |
| `wave.build.k8s.storage.mountPath`         | Volume mount path on wave build Kubernetes pods.              |        | true     |
| `wave.build.k8s.labels`                    | Labels to set on wave build kubernetes pods.                              |  | true     |
| `wave.build.k8s.node-selector`             | Node selector configuration for wave build kubernetes pods.                   |  | true     |
| `wave.build.k8s.service-account`           | Service account name for wve kubernetes cluster.         |  | true     |
| `wave.build.k8s.resources.requests.cpu`    | Amount of [CPU resources](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#resource-units-in-kubernetes) to allocate to wave build processes, e.g. `2` or `1500m`. |                        | true     |
| `wave.build.k8s.resources.requests.memory` | Amount of [memory resources](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#resource-units-in-kubernetes) to allocate to wave build processes, e.g. `3Gi` or `2000Mi`.           |                        | true     |


## Container scan process configuration

| Variable                      | Description                                                            | Default Value  / Environment Variable | Optional |
|-------------------------------|------------------------------------------------------------------------|---------------------------------------|----------|
| `wave.scan.enabled`                   | Enable or disable vulnerability scanning.                              | `false`                               | false    |
| `wave.scan.severity`                  | Severity level for vulnerability scanning. e.g. `MEDIUM,HIGH,CRITICAL`. | `LOW,MEDIUM,HIGH,CRITICAL`            | true     |
| `wave.scan.image.name`        | Docker Image used for container securioty scanning.                    | `aquasec/trivy:0.47.0`                | false    |


### Kubernetes configuration for Wave scan process

**Note**: If you will use Kubernetes.

| Variable                      | Description                                                      | Default Value / Environment Variable  | Optional |
|-------------------------------|------------------------------------------------------------------|--------------------------------------|----------|
| `wave.scan.k8s.resources.requests.cpu`        | Allocate number of CPUs for scanning process in kubernetes        |                                      | true     |
| `wave.scan.k8s.resources.requests.memory`        | Allocate memory for scanning process in kubernetes. e.g. `1000Mi`. |                                      | true     |


## Rate limit configuration

**Note**: If you enable rate limiting in wave by adding `rate-limit` in micronaut environment.

| Variable                      | Description                                         | Default Value / Environment Variable | Optional |
|-------------------------------|-----------------------------------------------------|-----------------------------------|----------|
| `rate-limit.build.anonymous`          | Rate limit configuration for anonymous build requests.            |      `10/1h`   | false    |
| `rate-limit.build.authenticated`      | Rate limit configuration for authenticated build requests.        |    `10/1m`    | false    |
| `rate-limit.pull.anonymous`           | Rate limit configuration for anonymous pull requests.             |      `100/1h`  | false    |
| `rate-limit.pull.authenticated`       | Rate limit configuration for authenticated pull requests.         | `100/1m`      | false    |


## Database and cache configuration

| Variable             | Description                  | Default Value / Environment Variable | Optional |
|----------------------|------------------------------|--------------------------------------------------|----------|
| `redis.uri`          | URI for connecting to Redis. | `redis://${REDIS_HOST:redis}:${REDIS_PORT:6379}` | false    |
| `redis.pool.enabled` | Enable redis pool.           | `true`                                             | true     |
| `surrealdb.ns`       | Surreal database namespace.  | `${SURREALDB_NS}`                                | false    |
| `surrealdb.db`       | Surreal database name.       | `${SURREALDB_DB}`                                | false    |
| `surrealdb.url`      | Surreal database url.        | `${SURREALDB_URL}`                               | false    |
| `surrealdb.user`     | Surreal database username.   | `${SURREALDB_USER}`                              | false    |
| `surrealdb.password` | Surreal database password.    | `${SURREALDB_PASSWORD`                               | false    |
| `surrealdb.init-db`  | Should initiate surreal DB.   |                           | true     |


## Email configuration

| Variable                      | Description                                         | Default Value / Environment Variable | Optional |
|-------------------------------|-----------------------------------------------------|-------------------------------------------|----------|
| `mail.from`                          | Email address for sending mail from wave.                         |       | false    |


## Jackson configuration

| Variable                      | Description                                         | Default Value / Environment Variable | Optional |
|-------------------------------|-----------------------------------------------------|-------------------------------------------|----------|
| `jackson.serialization.writeDatesAsTimestamps` | Write dates as timestamps in jackson serialization. | `false`                                  | false    |


## Micronaut specific Configuration

| Variable                    | Description                                                                                 | Default Value / Environment Variable | Optional |
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


## Notes
- Refer to the official Micronaut documentation for more details on available configuration properties: [Micronaut Configuration Reference](https://docs.micronaut.io/latest/guide/index.html#config)
