---
title: API reference
---

This page summarizes the API provided by the Wave container service.

## `/container-token`

This endpoint allows you to submit a request to access a private container repository via Wave, or build a container image on-the-fly with a Dockerfile or Conda recipe file.

The endpoint returns the name of the container request made available by Wave.

### Request

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

| Attribute                           | Description                                                                                                                                                    |
| ----------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `containerImage`                    | Name of the container to be served, e.g., `docker.io/library/ubuntu:latest` (optional). If omitted, the `containerFile` must be provided.                      |
| `containerConfig.entrypoint`        | The container entrypoint command e.g. `['/bin/bash']`                                                                                                          |
| `containerConfig.cmd`               | The launch command to be used by the Wave container, e.g., `['echo', 'Hello world']` (optional).                                                               |
| `containerConfig.env`               | The environment variables to be defined in the Wave container e.g. `['FOO=one','BAR=two']` (optional).                                                         |
| `containerConfig.workingDir`        | The work directory to be used in the Wave container e.g. `/some/work/dir` (optional).                                                                          |
| `containerConfig.layers.location`   | Either a HTTP URL from where a container layer tar gzip file should be downloaded or tar gzip file base64 encoded and prefixed with `data:` string (optional). |
| `containerConfig.layers.tarDigest`  | The SHA256checksum of the provided tar file e.g. `sha256:a7c724b02...`.                                                                                        |
| `containerConfig.layers.gzipDigest` | The SHA256 checksum of the provided layer tar gzip file e.g. `sha256:a7c724b02...`.                                                                            |
| `containerConfig.layers.gzipSize`   | The size in bytes of the the provided layer tar gzip file.                                                                                                     |
| `containerFile`                     | Dockerfile used for building a new container encoded in base64 (optional). When provided, the attribute `containerImage` should be omitted.                    |
| `condaFile`                         | Conda environment file encoded as base64 string.                                                                                                               |
| `spackFile`                         | Spack recipe file encoded as base64 string.                                                                                                                    |
| `containerPlatform`                 | Target container architecture of the built container e.g. `linux/amd64` (optional). Currently only supporting amd64 and arm64.                                 |
| `buildRepository`                   | Container repository where container builds should be pushed e.g. `docker.io/user/my-image` (optional).                                                        |
| `cacheRepository`                   | Container repository used to cache build layers `docker.io/user/my-cache` (optional).                                                                          |
| `timestamp`                         | Request submission timestap using ISO-8601.                                                                                                                    |
| `fingerprint`                       | Request unique fingerprint.                                                                                                                                    |
| `freeze`                            | The container provisioned will be stored in the specified repository in a permanently.                                                                         |
| `towerEndpoint`                     | Seqera Platform service endpoint from where container repositories credentials are retrieved (optional). Default `https://api.tower.nf`.                       |
| `towerAccessToken`                  | Access token of the user account granting the access to the Seqera Platform service specified via `towerEndpoint` (optional).                                  |
| `towerWorkspaceId`                  | ID of the Seqera Platform workspace from where the container repositories credentials are retrieved (optional). When omitted the personal workspace is used.   |

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

## Metrics APIs

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
