---
title: API documentation
description: Endpoints for the Wave API
hide:
    - navigation
    - toc
    - footer
---

# API documentation

This page summarizes the API provided by the Wave service.

!!! api-get "/container-token"

    This endpoint allows submitting a request either for accessing a private container repository via Wave
    or bulding a container image on-the-fly providing a Dockerfile or Conda recipe file.

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
        towerAccessToken: string,
        towerRefreshToken: string,
        towerEndpoint: string,
        towerWorkspaceId: number,
    }
    ```

    | Attribute                     | Description                          |
    | ----------------------------- | ------------------------------------ |
    | `containerImage`              | Name of the container to be served e.g. `docker.io/library/ubuntu:latest` (optional). If omitted the `containerFile` should be provided. |
    | `containerConfig.entrypoint`  | The container entrypoint command e.g. `['/bin/bash']` |
    | `containerConfig.cmd`         | The launching command that should be used by the Wave container e.g. `['echo', 'Hello world']` (optional). |
    | `containerConfig.env`         | The environment variables to be defined in the Wave container e.g. `['FOO=one','BAR=two']` (optional). |
    | `containerConfig.workingDir`          | The work directory to be used in the Wave container e.g. `/some/work/dir` (optional). |
    | `containerConfig.layers.location`     | Either a HTTP URL from where a container layer tar gzip file should be downloaded or tar gzip file base64 encoded and prefixed with `data:` string (optional). |
    | `containerConfig.layers.tarDigest`    | The SHA256checksum of the provided tar file e.g. `sha256:a7c724b02...`. |
    | `containerConfig.layers.gzipDigest`   | The SHA256 checksum of the provided layer tar gzip file e.g. `sha256:a7c724b02...`. |
    | `containerConfig.layers.gzipSize`     | The size in bytes of the the provided layer tar gzip file. |
    | `containerFile`               | Dockerfile used for building a new container encoded in base64 (optional). When provided, the attribute `containerImage` should be omitted. |
    | `condaFile`                   | Conda environment file encoded as base64 string. |
    | `spackFile`                   | Spack recipe file encoded as base64 string.     |
    | `containerPlatform`           | Target container architecture of the built container e.g. `linux/amd64` (optional). Currently only supporting amd64 and arm64. |
    | `buildRepository`             | Container repository where container builds should be pushed e.g. `docker.io/user/my-image` (optional). |
    | `cacheRepository`             | Container repository used to cache build layers `docker.io/user/my-cache` (optional). |
    | `timestamp`                   | Request submission timestap using ISO-8601. |
    | `fingerprint`                 | Request unique fingerprint. |
    | `towerEndpoint`               | Tower service endpoint from where container repositories credentials are retrieved (optional). Default `https://api.tower.nf`. |
    | `towerAccessToken`            | Access token of the user account granting the access to the Tower service specified via `towerEndpoint` (optional). |
    | `towerWorkspaceId`        | ID of the Tower workspace from where the container repositories credentials are retrieved (optional). When omitted the personal workspace is used. |

    ### Response

    ```json
    {
        containerToken: string,
        targetImage: string,
        expiration: string
    }
    ```

    | Attribute         | Description                          |
    | ----------------- | ------------------------------------ |
    | `containerToken`  | The unique token identifying the Wave container request e.g. `0123456789`.                |
    | `targetImage`     | The Wave container image name e.g. `wave.seqera.io/wt/0123456789/library/ubuntu:latest`.  |
    | `expiration`      | The expiration timestamp of the Wave container using ISO-8601 format.                     |

!!! api-get "/validate-creds"

    It allows validating the access credentials of a container registry service.

    ### Request

    ```json
    {
        userName: string,
        password: string,
        registry: string
    }
    ```

    | Attribute         | Description                          |
    | ----------------- | ------------------------------------ |
    | `userName`        | The registry service username (mandatory).    |
    | `password`        | The registry serviec password (mandatory).    |
    | `registry`        | The registry host name e.g. `docker.io`, `quay.io`, etc. Note: protocol prefix should be provided. When omitted it defaults to `docker.io`. |

    ### Response

    | HTTP status code  | Description                           |
    | ----------------- | ------------------------------------- |
    | `200`             | The credentials are valid.                |
    | `400`             | The credentials provided are not valid.   |

!!! api-get "/service-info"

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
