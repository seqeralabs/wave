---
title: API
---

This page summarizes the API provided by the Wave container service.

**API limits**

The Wave service implements API rate limits for API calls. Authenticated users have higher rate limits than anonymous users.

If an access token is provided, the following rate limits apply:

- 250 container builds per hour
- 2,000 container pulls per minute

If an access token isn't provided, the following rate limits apply:

- 25 container builds per day
- 100 container pulls per hour

## POST `/container-token`

Deprecated endpoint allows you to submit a request to access a private container registry via Wave, or build a container image on-the-fly with a Dockerfile or Conda recipe file.

The endpoint returns the name of the container request made available by Wave.

:::important

This API endpoint is deprecated in current versions of Wave.

:::

### Request body

```json
{
    containerImage: string,
    containerFile: string,
    containerConfig: {
        entrypoint: string[],
        cmd: string[],
        env: string[],
        workingDir: string
        layers: [
            {
                location: string,
                gzipDigest: string,
                gzipSize: string,
                tarDigest: string
            },
            ...
        ]
    },
    condaFile: string,
    containerPlatform: string,
    buildRepository: string,
    cacheRepository: string,
    timestamp: string,
    fingerprint: string,
    freeze: boolean,
    towerAccessToken: string,
    towerRefreshToken: string,
    towerEndpoint: string,
    towerWorkspaceId: number,
}
```

#### Container token request attributes

| Attribute                           | Description                                                                                                                                        |
| ----------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `containerImage`                    | Name of the container to be served, e.g., `docker.io/library/ubuntu:latest` (optional). If omitted, the `containerFile` must be provided.          |
| `containerConfig.entrypoint`        | The container entrypoint command, e.g., `['/bin/bash']`.                                                                                             |
| `containerConfig.cmd`               | The launch command to be used by the Wave container, e.g., `['echo', 'Hello world']` (optional).                                                   |
| `containerConfig.env`               | The environment variables to be defined in the Wave container, e.g., `['FOO=one','BAR=two']` (optional).                                             |
| `containerConfig.workingDir`        | The work directory to be used in the Wave container, e.g., `/some/work/dir` (optional).                                                              |
| `containerConfig.layers.location`   | Specifies a container image layer stored as a tar.gz file (optional). Either a HTTP URL to the file or a base64 encoded string prefixed with `data:`. |
| `containerConfig.layers.tarDigest`  | The SHA256checksum of the provided tar file, e.g., `sha256:a7c724b02...`.                                                                            |
| `containerConfig.layers.gzipDigest` | The SHA256 checksum of the provided layer tar gzip file, e.g., `sha256:a7c724b02...`.                                                                |
| `containerConfig.layers.gzipSize`   | The size in bytes of the the provided layer tar gzip file.                                                                                         |
| `containerFile`                     | Dockerfile used for building a new container encoded in base64 (optional). When provided, the attribute `containerImage` must be omitted.        |
| `condaFile`                         | Conda environment file encoded as base64 string.                                                                                                   |
| `containerPlatform`                 | Target container architecture of the built container, e.g., `linux/amd64` (optional). Currently only supporting amd64 and arm64.                     |
| `buildRepository`                   | Container repository where container builds should be pushed, e.g., `docker.io/user/my-image` (optional).                                            |
| `cacheRepository`                   | Container repository used to cache build layers `docker.io/user/my-cache` (optional).                                                              |
| `timestamp`                         | Request submission timestamp using ISO-8601.                                                                                                        |
| `fingerprint`                       | Request unique fingerprint.                                                                                                                        |
| `freeze`                            | Freeze requires buildRepository to push the build container to a user-defined repository. This provides the container URL from the user-defined repository, not the Wave generated URL. This URL won't change.                                                             |
| `towerEndpoint`                     | Seqera Platform service endpoint from where container registry credentials are retrieved (optional). Default `https://api.cloud.seqera.io`.                       |
| `towerAccessToken`                  | Access token of the user account granting access to the Seqera Platform service specified via `towerEndpoint` (optional).                      |
| `towerWorkspaceId`                  | ID of the Seqera Platform workspace from where the container registry credentials are retrieved (optional). When omitted the personal workspace is used. |

### Response

```json
{
    containerToken: string,
    targetImage: string,
    expiration: string
}
```

| Attribute        | Description                                                                              |
| ---------------- | ---------------------------------------------------------------------------------------- |
| `containerToken` | The unique token identifying the Wave container request, e.g., `0123456789`.               |
| `targetImage`    | The Wave container image name, e.g., `wave.seqera.io/wt/0123456789/library/ubuntu:latest`. |
| `expiration`     | The expiration timestamp of the Wave container using ISO-8601 format.                    |

## POST `/v1alpha2/container`

This endpoint allows you to submit a request to access a private container registry via Wave, or build a container image on-the-fly with a Dockerfile or Conda recipe file.

The endpoint returns the name of the container request made available by Wave.

### Request body

```json
{
    containerImage: string,
    containerFile: string,
    containerConfig: {
        entrypoint: string[],
        cmd: string[],
        env: string[],
        workingDir: string
        layers: [
            {
                location: string,
                gzipDigest: string,
                gzipSize: string,
                tarDigest: string
            },
            ...
        ]
    },
    condaFile: string,
    containerPlatform: string,
    buildRepository: string,
    cacheRepository: string,
    timestamp: string,
    fingerprint: string,
    freeze: boolean,
    towerAccessToken: string,
    towerRefreshToken: string,
    towerEndpoint: string,
    towerWorkspaceId: number,
    packages: {
                  type: string,
                  environment: string,
                  entries: string[],
                  channels: string[],
                  condaOpts: {
                                mambaImage: string,
                                commands: string[],
                                basePackages: string
                              }

              },
    nameStrategy: string
}
```

#### Container token request attributes

| Attribute                           | Description                                                                                                                                        |
| ----------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `containerImage`                    | Name of the container to be served, e.g., `docker.io/library/ubuntu:latest` (optional). If omitted, the `containerFile` must be provided.          |
| `containerConfig.entrypoint`        | The container entrypoint command, e.g., `['/bin/bash']`.                                                                                             |
| `containerConfig.cmd`               | The launch command to be used by the Wave container, e.g., `['echo', 'Hello world']` (optional).                                                   |
| `containerConfig.env`               | The environment variables to be defined in the Wave container, e.g., `['FOO=one','BAR=two']` (optional).                                             |
| `containerConfig.workingDir`        | The work directory to be used in the Wave container, e.g., `/some/work/dir` (optional).                                                              |
| `containerConfig.layers.location`   | Specifies a container image layer stored as a tar.gz file (optional). Either a HTTP URL to the file or a base64 encoded string prefixed with `data:`. |
| `containerConfig.layers.tarDigest`  | The SHA256checksum of the provided tar file, e.g., `sha256:a7c724b02...`.                                                                            |
| `containerConfig.layers.gzipDigest` | The SHA256 checksum of the provided layer tar gzip file, e.g., `sha256:a7c724b02...`.                                                                |
| `containerConfig.layers.gzipSize`   | The size in bytes of the the provided layer tar gzip file.                                                                                         |
| `containerFile`                     | Dockerfile used for building a new container encoded in base64 (optional). When provided, the attribute `containerImage` must be omitted.        |
| `condaFile`                         | Conda environment file encoded as base64 string.                                                                                                   |
| `containerPlatform`                 | Target container architecture of the built container, e.g., `linux/amd64` (optional). Currently only supporting amd64 and arm64.                     |
| `buildRepository`                   | Container repository where container builds should be pushed, e.g., `docker.io/user/my-image` (optional).                                            |
| `cacheRepository`                   | Container repository used to cache build layers `docker.io/user/my-cache` (optional).                                                              |
| `timestamp`                         | Request submission timestamp using ISO-8601.                                                                                                        |
| `fingerprint`                       | Request unique fingerprint.                                                                                                                        |
| `freeze`                            | Freeze requires buildRepository to push the build container to a user-defined repository. This provides the container URL from the user-defined repository, not the Wave generated URL. This URL won't change.                                                             |
| `towerEndpoint`                     | Seqera Platform service endpoint from where container registry credentials are retrieved (optional). Default `https://api.cloud.seqera.io`.                       |
| `towerAccessToken`                  | Access token of the user account granting access to the Seqera Platform service specified via `towerEndpoint` (optional).                      |
| `towerWorkspaceId`                  | ID of the Seqera Platform workspace from where the container registry credentials are retrieved (optional). When omitted the personal workspace is used. |
| `packages`                          | This object specifies Conda or CRAN packages environment information.                                                                                         |
| `environment`                       | The package environment file encoded as a base64 string.                                                                                                       |
| `type`                              | This represents the type of package builder. Use `CONDA` or `CRAN`.                                                                                   |
| `entries`                           | List of the packages names.                                                                                                                                    |
| `channels`                          | List of Conda channels or CRAN repositories, which will be used to download packages.                                                                                               |
| `condaOpts`                         | Conda build options (when type is CONDA).                                                                                                                      |
| `mambaImage`                        | Name of the Docker image used to build Conda containers.                                                                                              |
| `commands`                          | Command to be included in the container.                                                                                                                       |
| `basePackages`                      | Names of base packages.                                                                                                                                        |
| `baseImage`                         | Base image for the final stage of multi-stage builds (for Conda/Pixi).                                                                                        |
| `pixiOpts`                          | Pixi build options (when type is CONDA and buildTemplate is `pixi/v1`).                                                                                        |
| `pixiImage`                         | Name of the Docker image used for Pixi package manager (e.g., `ghcr.io/prefix-dev/pixi:latest`).                                                              |
| `cranOpts`                          | CRAN build options (when type is CRAN).                                                                                                                        |
| `rImage`                            | Name of the R Docker image used to build CRAN containers (e.g., `rocker/r-ver:4.4.1`).                                                                         |
| `buildTemplate`                     | The build template to use for container builds. Supported values: `pixi/v1` (Pixi with multi-stage builds), `micromamba/v2` (Micromamba 2.x with multi-stage builds). Default: standard conda/micromamba v1 template. |
| `nameStrategy`                      | The name strategy to be used to create the name of the container built by Wave. Its values can be `none`, `tagPrefix`, or `imageSuffix`.                       |                                                     |

### Response

```json
{
    containerToken: string,
    targetImage: string,
    expiration: string,
    buildId: string,
    cached: boolean
}
```

#### Container token response attributes

| Attribute        | Description                                                                              |
| ---------------- | ---------------------------------------------------------------------------------------- |
| `containerToken` | The unique token identifying the Wave container request, e.g., `0123456789`.               |
| `targetImage`    | The Wave container image name, e.g., `wave.seqera.io/wt/0123456789/library/ubuntu:latest`. |
| `expiration`     | The expiration timestamp of the Wave container using ISO-8601 format.                    |
| `cached`         | Indicates if the requested image is built or in progress.                                |

### Examples

1. Create Docker image with Conda packages:

##### Request

```shell
curl --location 'http://localhost:9090/v1alpha2/container' \
--header 'Content-Type: application/json' \
--data '{
    "packages":{
        "type": "CONDA",
        "entries": ["salmon", "bwa"],
        "channels": ["conda-forge", "bioconda"]
    }
}'
```

#### Response

```json
{
    "containerToken":"732b73aa17c8",
    "targetImage":"0625dce899da.ngrok.app/wt/732b73aa17c8/hrma017/dev:salmon_bwa--5e49881e6ad74121",
    "expiration":"2024-04-09T21:19:01.715321Z",
    "buildId":"5e49881e6ad74121_1",
    "cached":false,
    "freeze":false
}
```

2. Create Singularity image with Conda packages:

##### Request

```shell
curl --location 'http://localhost:9090/v1alpha2/container' \
--header 'Content-Type: application/json' \
--data '{
    "format": "sif",
    "containerPlatform": "arm64",
    "packages":{
        "type": "CONDA",
        "entries": ["salmon"],
        "channels": ["conda-forge", "bioconda"]
    },
    "freeze": true,
    "buildRepository": <CONTAINER_REPOSITORY>,
    "towerAccessToken":<YOUR_SEQERA_PLATFORM_TOWER_TOKEN>,
    "towerEndpoint": "http://localhost:8008/api"
}'
```

#### Response

```json
{
    "targetImage":"oras://<CONTAINER_REPOSITORY>:salmon--6c084f2e43f86a78",
    "buildId":"6c084f2e43f86a78_1",
    "cached":false,
    "freeze":true
}
```

:::note
You must add your container registry credentials in Seqera Platform to use the freeze feature. This is a requirement for Singularity.
:::

3. Create Docker image with CRAN packages:

##### Request

```shell
curl --location 'http://localhost:9090/v1alpha2/container' \
--header 'Content-Type: application/json' \
--data '{
    "packages":{
        "type": "CRAN",
        "entries": ["dplyr", "ggplot2"],
        "channels": ["cran"],
        "cranOpts": {
            "rImage": "rocker/r-ver:4.4.1",
            "basePackages": "littler r-cran-docopt"
        }
    }
}'
```

#### Response

```json
{
    "requestId": "22d3c6c1cb06",
    "containerToken": "22d3c6c1cb06",
    "targetImage": "wave.seqera.io/wt/22d3c6c1cb06/wave/build:49b26ca0c3a07b1b",
    "expiration": "2025-11-09T02:50:23.254497148Z",
    "containerImage": "private.cr.seqera.io/wave/build:49b26ca0c3a07b1b",
    "buildId": "bd-49b26ca0c3a07b1b_1",
    "cached": false,
    "freeze": false,
    "mirror": false,
    "scanId": "sc-a6acedfe6969f4bf_1"
}
```

4. Create Singularity image with CRAN packages:

##### Request

```shell
curl --location 'https://wave.seqera.io/v1alpha2/container' \
--header 'Content-Type: application/json' \
--data '{
    "format": "sif",
    "containerPlatform": "linux/amd64",
    "packages":{
        "type": "CRAN",
        "entries": ["tidyverse", "data.table"],
        "channels": ["cran"],
        "cranOpts": {
            "rImage": "rocker/r-ver:4.4.1",
            "basePackages": "build-essential"
        }
    },
    "freeze": true,
    "buildRepository": "<CONTAINER_REPOSITORY>", # hrma017/test
    "towerAccessToken": "<TOKEN>"
}'
```

#### Response

```json
{
    "requestId": "6706d70da258",
    "targetImage": "oras://hrma017/test:a4fd48144607aaa7",
    "containerImage": "oras://hrma017/test:a4fd48144607aaa7",
    "buildId": "bd-a4fd48144607aaa7_1",
    "freeze": true,
    "mirror": false,
    "succeeded": true
}
```

5. Create Docker image with Pixi v1 template (multi-stage build):

##### Request

```shell
curl --location 'https://wave.seqera.io/v1alpha2/container' \
--header 'Content-Type: application/json' \
--data '{
    "containerPlatform": "linux/amd64",
    "format": "docker",
    "buildTemplate": "pixi/v1",
    "packages":{
        "type": "CONDA",
        "entries": ["numpy", "pandas", "scikit-learn"],
        "channels": ["conda-forge"],
        "pixiOpts": {
            "pixiImage": "ghcr.io/prefix-dev/pixi:latest",
            "basePackages": "conda-forge::procps-ng",
            "baseImage": "ubuntu:24.04",
            "commands": []
        }
    }
}'
```

#### Response

```json
{
    "requestId":"bf31a6445b41",
    "containerToken":"bf31a6445b41",
    "targetImage":"https://wave.seqera.io/wt/bf31a6445b41/hrma017/dev:numpy_pandas_scikit-learn--ad24e45802adb349",
    "expiration":"2025-12-02T11:47:55.908498Z",
    "containerImage":"hrma017/dev:numpy_pandas_scikit-learn--ad24e45802adb349",
    "buildId":"bd-ad24e45802adb349_1",
    "cached":false,
    "freeze":false,
    "mirror":false,
    "scanId":"sc-98fd615516bd93d6_1"
}
```

6. Create Docker image with Micromamba v2 template (multi-stage build):

##### Request

```shell
curl --location 'https://wave.seqera.io/v1alpha2/container' \
--header 'Content-Type: application/json' \
--data '{
    "containerPlatform": "linux/amd64",
    "format": "docker",
    "buildTemplate": "micromamba/v2",
    "packages":{
        "type": "CONDA",
        "entries": ["bwa=0.7.15", "salmon=1.10.0", "samtools=1.17"],
        "channels": ["conda-forge", "bioconda"],
        "condaOpts": {
            "mambaImage": "mambaorg/micromamba:2-amazon2023",
            "basePackages": "conda-forge::procps-ng",
            "baseImage": "ubuntu:24.04",
            "commands": []
        }
    }
}'
```

#### Response

```json
{
    "requestId":"248eefc1fc14",
    "containerToken":"248eefc1fc14",
    "targetImage":"wave.local/wt/248eefc1fc14/hrma017/dev:bwa-0.7.15_salmon-1.10.0_samtools-1.17--40730eb5c2c3dc6e",
    "expiration":"2025-12-02T12:14:59.672505Z",
    "containerImage":"hrma017/dev:bwa-0.7.15_salmon-1.10.0_samtools-1.17--40730eb5c2c3dc6e",
    "buildId":"bd-40730eb5c2c3dc6e_1",
    "cached":false,
    "freeze":false,
    "mirror":false,
    "scanId":"sc-f36486d1a7e3053a_1"
}
```

7. Create Singularity image with Pixi v1 template (multi-stage build):

##### Request

```shell
curl --location 'https://wave.seqera.io/v1alpha2/container' \
--header 'Content-Type: application/json' \
--data '{
    "containerPlatform": "linux/amd64",
    "format": "sif",
    "buildTemplate": "pixi/v1",
    "packages":{
        "type": "CONDA",
        "entries": ["numpy", "pandas", "scikit-learn"],
        "channels": ["conda-forge"],
        "pixiOpts": {
            "pixiImage": "ghcr.io/prefix-dev/pixi:latest",
            "basePackages": "conda-forge::procps-ng",
            "baseImage": "ubuntu:24.04",
            "commands": []
        }
    },
    "freeze": true,
    "buildRepository": "<CONTAINER_REPOSITORY>", # hrma017/test
    "towerAccessToken": "<TOKEN>"
}'
```

#### Response

```json
{
    "requestId":"7159b38c6c04",
    "targetImage":"oras://hrma017/test:numpy_pandas_scikit-learn--717309e30359606f",
    "containerImage":"oras://hrma017/test:numpy_pandas_scikit-learn--717309e30359606f",
    "buildId":"bd-717309e30359606f_1",
    "cached":false,
    "freeze":true,
    "mirror":false
}
```

8. Create Singularity image with Micromamba v2 template (multi-stage build):

##### Request

```shell
curl --location 'https://wave.seqera.io/v1alpha2/container' \
--header 'Content-Type: application/json' \
--data '{
    "containerPlatform": "linux/amd64",
    "format": "sif",
    "buildTemplate": "micromamba/v2",
    "packages":{
        "type": "CONDA",
        "entries": ["bwa=0.7.15", "salmon=1.10.0", "samtools=1.17"],
        "channels": ["conda-forge", "bioconda"],
        "condaOpts": {
            "mambaImage": "mambaorg/micromamba:2-amazon2023",
            "basePackages": "conda-forge::procps-ng",
            "baseImage": "ubuntu:24.04",
            "commands": []
        }
    },
    "freeze": true,
    "buildRepository": "<CONTAINER_REPOSITORY>", # hrma017/test
    "towerAccessToken": "<TOKEN>"
}'
```

#### Response

```json
{
    "requestId":"b93c35abca4e",
    "targetImage":"oras://hrma017/test:bwa-0.7.15_salmon-1.10.0_samtools-1.17--e85b5c89438aa3ff",
    "containerImage":"oras://hrma017/test:bwa-0.7.15_salmon-1.10.0_samtools-1.17--e85b5c89438aa3ff",
    "buildId":"bd-e85b5c89438aa3ff_1",
    "cached":false,
    "freeze":true,
    "mirror":false
}
```

:::note
Multi-stage build templates (`pixi/v1` and `micromamba/v2`) create optimized container images by separating the build environment from the final runtime environment. This results in smaller container images that only contain the installed packages and runtime dependencies, without the build tools.
:::

## GET `/v1alpha1/builds/{buildId}/status`

Provides status of build against buildId passed as path variable

### Response

```json
{
    id: string,
    status: string,
    startTime: string,
    duration: string,
    succeeded: boolean
}
```

:::note
Status can only be `PENDING` or `COMPLETED`.
:::

### Example

```shell
% curl --location 'http://localhost:9090/v1alpha1/builds/6c084f2e43f86a78_1/status'
{
    "id":"6c084f2e43f86a78_1",
    "status":"COMPLETED",
    "startTime":"2024-04-09T20:31:35.355423Z",
    "duration":123.914989000,
    "succeeded":true
}
```

## GET `/v1alpha1/builds/{buildId}/logs`

Supply logs corresponding to the specified buildId within the API request.

### Response

```text
string
```

### Example

```
% curl --location 'http://localhost:9090/v1alpha1/builds/<BUILD_ID>/logs'
INFO[0001] Retrieving image manifest alpine:latest
INFO[0001] Retrieving image alpine:latest from registry index.docker.io
INFO[0002] Retrieving image manifest alpine:latest
INFO[0002] Returning cached image manifest
INFO[0002] Built cross stage deps: map[]
INFO[0002] Retrieving image manifest alpine:latest
INFO[0002] Returning cached image manifest
INFO[0002] Retrieving image manifest alpine:latest
INFO[0002] Returning cached image manifest
INFO[0002] Executing 0 build triggers
INFO[0002] Building stage 'alpine:latest' [idx: '0', base-idx: '-1']
INFO[0002] Skipping unpacking as no commands require it.
INFO[0002] Pushing image to <REPO>/<IMAGE>
INFO[0005] Pushed index.docker.io/<REPO>/<IMAGE>
```

## GET `/service-info`

Provides basic information about the service status.

### Response

```json
{
    serviceInfo: {
        version: string,
        commitId: string
    }
}
```

## POST `/v1alpha1/inspect`

This endpoint returns the metadata about provided container image

### Request

```json
{
    containerImage: string,
    towerAccessToken: string,
    towerEndpoint: string,
    towerWorkspaceId: string
}
```

#### Container inspect request attributes

| Attribute                           | Description                                                                                                                                                 |
| ----------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `containerImage`                    | Name of the container to be inpected, e.g., `docker.io/library/ubuntu:latest`                                                                               |
| `towerEndpoint`                     | Seqera Platform service endpoint from where container registry credentials are retrieved (optional). Default `https://api.cloud.seqera.io`.             |
| `towerAccessToken`                  | Access token of the user account granting the access to the Seqera Platform service specified via `towerEndpoint` (optional).                               |
| `towerWorkspaceId`                  | ID of the Seqera Platform workspace from where the container registry credentials are retrieved (optional). When omitted the personal workspace is used.|

### Response

```json
{
    container: {
        registry: string,
        hostName: string,
        imageName: string,
        reference: string,
        digest: string,
        config: {
            architecture: string,
            config: {
                attachStdin: boolean,
                attachStdout: boolean,
                attachStderr: boolean,
                tty: boolean,
                env: string[],
                cmd: string[],
                image: string
            },
            container: string,
            created: string,
            rootfs: {
                type: string,
                diff_ids: string[]
            }
        },
        manifest: {
            schemaVersion: integer,
            mediaType: string,
            config: {
                mediaType: string,
                digest: string,
                size: integer
            },
            layers: [
                {
                    mediaType: string,
                    digest: string,
                    size: integer
                }
            ]
        },
        v1: boolean,
        v2: boolean,
        oci: boolean
    }
}
```

:::note
You can find the explanation of the response attributes (here)[https://github.com/opencontainers/image-spec/blob/main/spec.md]
:::

### Example

#### API call

```shell
curl --location 'http://localhost:9090/v1alpha1/inspect' \
--header 'Content-Type: application/json' \
--data '{
    "containerImage": "docker.io/<REPO>/<IMAGE>",
    "towerAccessToken": "<TOWER_TOKEN>",
    "towerEndpoint": "http://localhost:8000/api"
}'
```

##### Response

```json
{
    "container": {
        "registry": "docker.io",
        "hostName": "https://registry-1.docker.io",
        "imageName": "<WAVE_TOKEN>/<REPO>/<IMAGE>",
        "reference": "9b80535d04eceefd",
        "digest": "sha256:1fcabdb850dc7c46646b3796fca01aca5721330252b586058e0d326705374dd5",
        "config": {
            "architecture": "amd64",
            "config": {
                "env": [
                    "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
                ],
                "cmd": [
                    "/bin/sh"
                ],
                "image": "sha256:9a5ce069f40cfe0f2270eafbff0a0f2fa08f1add73571af9f78209e96bb8a5e9"
            },
            "container": "4189cbc534955765760c227f328ec1cdd52e8550681c2bf9f8f990b27b644f9c",
            "created": "2024-04-19T14:38:17.047396956Z",
            "rootfs": {
                "type": "layers",
                "diff_ids": [
                    "sha256:d4fc045c9e3a848011de66f34b81f052d4f2c15a17bb196d637e526349601820"
                ]
            }
        },
        "manifest": {
            "schemaVersion": 2,
            "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
            "config": {
                "mediaType": "application/vnd.docker.container.image.v1+json",
                "digest": "sha256:639823e18eb8b62cf43e92bac114ae35c03c07449e4ee5c10f8ebf8d033877d6",
                "size": 774
            },
            "layers": [
                {
                    "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
                    "digest": "sha256:4abcf20661432fb2d719aaf90656f55c287f8ca915dc1c92ec14ff61e67fbaf8",
                    "size": 3408729
                }
            ]
        },
        "v1": false,
        "v2": true,
        "oci": false
    }
}
```

## Metrics APIs based on Redis

These APIs provide usage (builds and pulls) metrics of Wave for a specific date and/or a specific organization.
These APIs require basic authentication, so you must provide a username and password while calling these APIs.

All Metrics API endpoints use these query parameters:

| Name | Description                                                           | sample Value |
|------|-----------------------------------------------------------------------|--------------|
| date | Format: `yyyy-mm-dd`, The date of the required metrics.               | 2024-04-08   |
| org  | Domain of the organization used in emails, e.g., `org=seqera.io` | seqera.io    |

### Build Metrics API

These APIs are used to retrieve metrics about container builds performed by Wave.

### GET `/v1alpha2/metrics/builds`

This endpoint is used to retrieve the builds performed by Wave.

### Response

```json
{
    metric: "builds",
    count: integer,
    orgs: {
        String: integer,
        String: integer,
        ...
    }
}
```

#### Examples

```shell
curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/builds"
{
    "metric": "builds",
    "count": 18,
    "orgs": {
        "seqera.io": 13,
        "gmail.com": 5
    }
}
```

```shell
curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/builds?date=2024-04-08&org=seqera.io"
{"count":4}
```

```shell
curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/builds?date=2024-04-08"
{
    "metric": "builds",
    "count": 8,
    "orgs": {
        "gmail.com": 4,
        "seqera.io": 4
    }
}
```

```shell
curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/builds?org=seqera.io"
{
    "metric": "builds",
    "count": 13,
    "orgs": {
        "seqera.io": 13
    }
}
```
### Pull Metrics API

These APIs are used to get the metrics about the container pulls through Wave.

### GET `/v1alpha2/metrics/pulls`

This endpoint is used to get the pulls performed through Wave.

### Response

```json
{
    metric: "pulls",
    count: integer,
    orgs: {
        String: integer,
        String: integer,
        ...
    }
}
```

#### Examples

```shell
curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/pulls"
{
    "metric": "pulls",
    "count": 11,
    "orgs": {
        "seqera.io": 7,
        "gmail.com": 4
    }
}
```

```shell
curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/pulls?date=2024-04-08&org=seqera.io"
{"count":5}
```

```shell
curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/pulls?date=2024-04-08"
{
    "metric": "pulls",
    "count": 4,
    "orgs": {
        "seqera.io": 1,
        "gmail.com": 3
    }
}
```

```shell
curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/pulls?org=seqera.io"
{
    "metric": "pulls",
    "count": 7,
    "orgs": {
        "seqera.io": 7
    }
}
```

### Fusion Pull Metrics API

These APIs are used to get the metrics about the Fusion-based container pulls through Wave.

### GET `/v1alpha2/metrics/fusion/pulls`

This endpoint is used to get the pulls of Fusion-based containers performed through Wave.

### Response

```json
{
    metric: "fusion",
    count: integer,
    orgs: {
        String: integer,
        String: integer,
        ...
    }
}
```

#### Examples

```shell
curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/fusion/pulls"
{
    "metric": "fusion",
    "count": 2,
    "orgs": {
        "seqera.io": 1,
        "gmail.com": 1
    }
}
```

```shell
curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/fusion/pulls?date=2024-04-08&org=seqera.io"
{"count":2}
```

```shell
curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/fusion/pulls?date=2024-04-08"
{
    "metric": "fusion",
    "count": 1,
    "orgs": {
        "gmail.com": 1
    }
}
```

```shell
curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/fusion/pulls?org=seqera.io"
{
    "metric": "fusion",
    "count": 1,
    "orgs": {
        "seqera.io": 1
    }
}
```
