---
title: API reference
---

This page summarizes the API provided by the Wave container service.

## POST `/container-token`

This endpoint allows you to submit a request to access a private container repository via Wave, or build a container image on-the-fly with a Dockerfile or Conda recipe file.

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
    spackFile: string,
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
#### container token request attributes 

| Attribute                           | Description                                                                                                                                        |
| ----------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `containerImage`                    | Name of the container to be served, e.g., `docker.io/library/ubuntu:latest` (optional). If omitted, the `containerFile` must be provided.          |
| `containerConfig.entrypoint`        | The container entrypoint command e.g. `['/bin/bash']`                                                                                              |
| `containerConfig.cmd`               | The launch command to be used by the Wave container, e.g., `['echo', 'Hello world']` (optional).                                                   |
| `containerConfig.env`               | The environment variables to be defined in the Wave container e.g. `['FOO=one','BAR=two']` (optional).                                             |
| `containerConfig.workingDir`        | The work directory to be used in the Wave container e.g. `/some/work/dir` (optional).                                                              |
| `containerConfig.layers.location`   | Either a HTTP URL from where a container layer tar gzip file should be downloaded or tar gzip file base64 encoded and prefixed with `data:` string (optional). |
| `containerConfig.layers.tarDigest`  | The SHA256checksum of the provided tar file e.g. `sha256:a7c724b02...`.                                                                            |
| `containerConfig.layers.gzipDigest` | The SHA256 checksum of the provided layer tar gzip file e.g. `sha256:a7c724b02...`.                                                                |
| `containerConfig.layers.gzipSize`   | The size in bytes of the the provided layer tar gzip file.                                                                                         |
| `containerFile`                     | Dockerfile used for building a new container encoded in base64 (optional). When provided, the attribute `containerImage` should be omitted.        |
| `condaFile`                         | Conda environment file encoded as base64 string.                                                                                                   |
| `spackFile`                         | Spack recipe file encoded as base64 string.                                                                                                        |
| `containerPlatform`                 | Target container architecture of the built container e.g. `linux/amd64` (optional). Currently only supporting amd64 and arm64.                     |
| `buildRepository`                   | Container repository where container builds should be pushed e.g. `docker.io/user/my-image` (optional).                                            |
| `cacheRepository`                   | Container repository used to cache build layers `docker.io/user/my-cache` (optional).                                                              |
| `timestamp`                         | Request submission timestap using ISO-8601.                                                                                                        |
| `fingerprint`                       | Request unique fingerprint.                                                                                                                        |
| `freeze`                            | The container provisioned will be stored in the specified repository in a permanently.                                                             |
| `towerEndpoint`                     | Seqera Platform service endpoint from where container repositories credentials are retrieved (optional). Default `https://api.cloud.seqera.io`.                       |
| `towerAccessToken`                  | Access token of the user account granting the access to the Seqera Platform service specified via `towerEndpoint` (optional).                      |
| `towerWorkspaceId`                  | ID of the Seqera Platform workspace from where the container repositories credentials are retrieved (optional). When omitted the personal workspace is used. |

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
| `containerToken` | The unique token identifying the Wave container request e.g. `0123456789`.               |
| `targetImage`    | The Wave container image name e.g. `wave.seqera.io/wt/0123456789/library/ubuntu:latest`. |
| `expiration`     | The expiration timestamp of the Wave container using ISO-8601 format.                    |

## POST `/v1alpha2/container`

This endpoint allows you to submit a request to access a private container repository via Wave, or build a container image on-the-fly with a Dockerfile or Conda recipe file.

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
    spackFile: string,
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
                  packages: string[],
                  channels: string[],
                  envFile: string,
                  condaOpts: {
                                mambaImage: string,
                                commands: string[],
                                basePackages: string
                              }
                  spackOpts:{
                                commands: string[],
                                basePackages: string
                            }                     
                  
              }
}
```

Note: You can read the description of al attributes except packages from [here](#container-token-request-attributes)


| Attribute      | Description                                                                 |
|----------------|-----------------------------------------------------------------------------|
| `packages`     | this object specifies conda or spack packages environment information       |
| `type`         | This represents the type of package builder. you can use `SPACK` or `CONDA` |
| `packages`     | List of the packages names                                                  |
| `channels`     | List of conda channels, which will be used to download packages             |
| `envFile`      | The package environment file encoded as a base64 string.                    |
| `mambaImage`   | Name of the docker image to be used in building conda containers            |
| `commands`     | Command to be included in the container                                     |
| `basePackages` | Names of base packages                                                      |

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

### Examples

1. Create docker image with conda packages

##### Request

```
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

```
{
    "containerToken":"732b73aa17c8",
    "targetImage":"0625dce899da.ngrok.app/wt/732b73aa17c8/hrma017/dev:salmon_bwa--5e49881e6ad74121",
    "expiration":"2024-04-09T21:19:01.715321Z",
    "buildId":"5e49881e6ad74121_1",
    "cached":false,
    "freeze":false
}
```

2. Create singularity image with conda packages

##### Request

```
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

```
{
    "targetImage":"oras://<CONTAINER_REPOSITORY>:salmon--6c084f2e43f86a78",
    "buildId":"6c084f2e43f86a78_1",
    "cached":false,
    "freeze":true
}
```

Note: You need to add your container registry credentials in seqera platform to use freeze feature which is a requirement for singularity.


## `/v1alpha1/builds/{buildId}/status`

Provides status of build against buildId passed as path variable

### Response

```json
{
    serviceInfo: {
        id: string,
        status: string,
        startTime: string,
        duration: string,
        succeeded: boolean
    }
}
```
Note: status can only be `PENDING` or `COMPLETED`.

### Example

```
% curl --location 'http://localhost:9090/v1alpha1/builds/6c084f2e43f86a78_1/status'
{
    "id":"6c084f2e43f86a78_1",
    "status":"COMPLETED",
    "startTime":"2024-04-09T20:31:35.355423Z",
    "duration":123.914989000,
    "succeeded":true
}
```

## `/service-info`

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

## Metrics APIs based on SurrealDB

These APIs provide usage (builds and pulls) metrics of Wave per IP and user.
These APIs required basic authentication, so you need to provide username and password while calling these APIs.

All Metrics API endpoints have these three common optional query parameters:

| Name      | Description                                                                                   | sample Value |
|-----------|-----------------------------------------------------------------------------------------------|--------------|
| startDate | Format: `yyyy-mm-dd`, The data of this date will be included in computing the response.       | 2024-02-29   |
| endDate   | Format: `yyyy-mm-dd`, The data of this date will be included in computing the response.       | 2024-02-29   |
| limit     | Integer from `0` to `1000` and default is `100`. It limits the number of records in response. | 10           |

Note: If you only provide startDate then endDate will be today's date. 

### Build Metrics APIs

These APIs are used to get the metrics about the container builds by Wave.

All build metrics API endpoints have one common optional query parameter:

| Name    | Description                                                                                                   | sample Value |
|---------|---------------------------------------------------------------------------------------------------------------|--------------|
| success | `true` or `false` is allowed and its used to get the metrics of successful container builds performed by Wave | true         |

Below are the different build metrics API endpoints:

### GET `/v1alpha1/metrics/builds`

This endpoint is used to get the total numbers of builds performed by Wave.

### Response

```json
{
    count: integer
}
```

#### Example

```
% curl -u foo:bar "http://localhost:9090/v1alpha1/metrics/builds?startDate=2024-02-29&endDate=2024-02-29&success=true"
{"count":2}
```


### GET `/v1alpha1/metrics/builds/ip`

This endpoint is used to get the total numbers of builds per IP performed by Wave.

### Response

```json
{
    result: {
        <IP_ADDRESS>: integer
    }
}
```

#### Example

```
% curl -u foo:bar "http://localhost:9090/v1alpha1/metrics/builds/ip?startDate=2024-02-29&endDate=2024-02-29&success=true"
{"result":{"127.0.0.1":2}}
```

### GET `/v1alpha1/metrics/builds/user`

This endpoint is used to get the total numbers of builds per User performed by Wave.

### Response

```json
{
    result: {
        <EMAIL_ADDRESS>: integer
    }
}
```

#### Example

```
% curl -u foo:bar "http://localhost:9090/v1alpha1/metrics/builds/user?startDate=2024-02-29&endDate=2024-02-29&success=true"
{"result":{"test_metrics@seqera.io":2}}
```

### Pull Metrics APIs

These APIs are used to get the metrics about the container pulls through Wave.

All pull metrics API endpoints have one common optional query parameter:

| Name   | Description                                                                                                | sample Value |
|--------|------------------------------------------------------------------------------------------------------------|--------------|
| fusion | `true` or `false` is allowed and its used to get the metrics of containers with fusion pulled through Wave | true         |

Below are the different pull metrics API endpoints:

### GET `/v1alpha1/metrics/pulls`

This endpoint is used to get the total numbers of containers pulled through Wave.

### Response

```json
{
    count: integer
}
```

#### Example

```
% curl -u foo:bar "http://localhost:9090/v1alpha1/metrics/pulls?startDate=2024-02-29&endDate=2024-02-29&fusion=true"
{"count":2}
```


### GET `/v1alpha1/metrics/pulls/ip`

This endpoint is used to get the total numbers of pulls through Wave per IP.

### Response

```json
{
    result: {
        <IP_ADDRESS>: integer
    }
}
```

#### Example

```
% curl -u foo:bar "http://localhost:9090/v1alpha1/metrics/pulls/ip?startDate=2024-02-29&endDate=2024-02-29&success=true"
{"result":{"127.0.0.1":2}}
```

### GET `/v1alpha1/metrics/pulls/user`

This endpoint is used to get the total numbers of pulls through Wave per user.

### Response

```json
{
    result: {
        <EMAIL_ADDRESS>: integer
    }
}
```

#### Example

```
% curl -u foo:bar "http://localhost:9090/v1alpha1/metrics/pulls/user?startDate=2024-02-29&endDate=2024-02-29&fusion=true"
{"result":{"test_metrics@seqera.io":2}}
```

## Metrics APIs based on Redis

These APIs provide usage (builds and pulls) metrics of Wave for a specific date and/or a specific organisation.
These APIs required basic authentication, so you need to provide username and password while calling these APIs.

All Metrics API endpoints have these two query parameters, at least one needs to provided to fetch the metrics:

| Name | Description                                                           | sample Value |
|------|-----------------------------------------------------------------------|--------------|
| date | Format: `yyyy-mm-dd`, The data of the required metrics.               | 2024-04-08   |
| org  | domain of the organisation used in their emails. e.g. `org=seqera.io` | seqera.io    |

### Build Metrics API

These APIs are used to get the metrics about the container builds by Wave.

### GET `/v1alpha2/metrics/builds`

This endpoint is used to get the total numbers of builds performed by Wave.

### Response

```json
{
    count: integer
}
```

#### Examples

```
% curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/builds?date=2024-04-08&org=seqera.io"
{"count":4}
```

```
% curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/builds?date=2024-04-08"
{"count":6}
```

```
% curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/builds?org=seqera.io"
{"count":4}
```
### Pull Metrics API

These APIs are used to get the metrics about the container pulls through Wave.

### GET `/v1alpha2/metrics/pulls`

This endpoint is used to get the total numbers of pulls performed through Wave.

### Response

```json
{
    count: integer
}
```

#### Examples

```
% curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/pulls?date=2024-04-08&org=seqera.io"
{"count":5}
```

```
% curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/pulls?date=2024-04-08"
{"count":7}
```

```
% curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/pulls?org=seqera.io"
{"count":5}
```
### Fusion Pull Metrics API

These APIs are used to get the metrics about the fusion based container pulls through Wave.

### GET `/v1alpha2/metrics/fusion/pulls`

This endpoint is used to get the total numbers of pulls of fusion based containers performed through Wave.

### Response

```json
{
    count: integer
}
```

#### Examples

```
% curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/fusion/pulls?date=2024-04-08&org=seqera.io"
{"count":2}
```

```
% curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/fusion/pulls?date=2024-04-08"
{"count":2}%
```

```
% curl -u foo:bar "http://localhost:9090/v1alpha2/metrics/fusion/pulls?org=seqera.io"
{"count":2}
```
