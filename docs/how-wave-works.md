---
title: How Wave works
description: How Wave provisions and serves container images on demand.
date: 2026-04-29
tags: [architecture, lifecycle, registry]
---

Wave builds, augments, and serves container images on demand. Clients submit a request that references an existing image or supplies build instructions. Wave returns an image URI that any standard container runtime can pull. Wave implements the Docker Registry v2 API and serves OCI-compatible manifests, so existing tooling works unchanged.

## Request lifecycles

Wave delivers images two ways. Most requests use a proxy lifecycle. Freeze and mirror use a pass-through lifecycle. Inspection and scanning fall outside both patterns.

For details on each capability, see [Features](./features/index.mdx).

### Proxy lifecycle

The proxy lifecycle covers [container augmentation](./features/augmentation.mdx), [private registry authentication](./features/authentication.mdx), and default [on-demand container builds](./features/container-builds.mdx). Wave stays in the pull path and the runtime fetches the image through Wave.

A Wave client (Nextflow, the Wave CLI, or any caller of the Wave API) submits a container request. Wave authenticates the caller against Seqera Platform when you supply a token. Wave returns an ephemeral image URI immediately. Builds and augmentation work run asynchronously in the background.

When the runtime pulls the URI, Wave holds the connection open until any in-progress work completes. Wave then serves the manifest. For augmentation, the manifest combines base layers from the source registry with the layers Wave injects. For builds, Wave proxies the manifest of the freshly built image.

### Pass-through lifecycle

The pass-through lifecycle covers [container freeze](./features/container-freezes.mdx) and [container mirroring](./features/mirroring.mdx). Wave returns a stable URI in a registry you control and stays out of the pull path.

Wave runs the build or copy in the background and pushes the result to your target registry. The runtime pulls from that registry directly. Pulls before the underlying job finishes fail rather than block. This is the opposite of the proxy lifecycle when a pull from a default on-demand build URI waits on Wave until the build completes, but a pull from a freeze or mirror URI fails fast because Wave is not in the pull path.

### Inspection and scanning

[Container inspection](./features/inspection.mdx) is a synchronous request. Wave queries the source registry and returns metadata in one round trip. There is no URI and no pull phase.

[Security scanning](./features/security.mdx) runs as a parallel asynchronous track. Scans do not block pulls. Clients fetch scan results through a separate API and can abort based on results.

## Image URIs

Wave returns one of two URI formats. Both embed a content-based build identifier, so identical inputs always resolve to the same URI and reuse the cached image.

### Ephemeral URIs

Augmentation and default on-demand build requests return ephemeral URIs. They suit single-use pipeline tasks. Ephemeral image names take this form:

```console
wave.seqera.io/wt/<access-token>/<image-path>:<tag>
```

The `<image-path>:<tag>` segment depends on the request. Builds use `wave/build:<checksum>`. Augmentation requests preserve the source image path, for example `library/alpine:latest`.

In this example:

- `<access-token>` is a 12-character random key, valid for the request lifetime (see [Ephemeral token access](#ephemeral-token-access)). Wave uses it to authorize the pull and to look up the registry credentials stored in Seqera Platform.
- `<checksum>` is a 16-character build identifier derived from the request inputs. Inputs include the container file, the Conda environment file, the target platform, the target repository, the build context, and the container config.

The expiry applies to the access token in the URI, not the image. On the hosted Wave service, Wave-built images stay in the private build registry for around seven days, governed by the registry's lifecycle policy. After the token expires, the image may still exist, but the ephemeral URI no longer authorizes a pull. To keep a stable, long-lived URI, use [container freeze](./features/container-freezes.mdx) and push the image to a registry you control.

### Stable URIs

Freeze and mirror return URIs that point at a registry you control. Stable URIs carry no access token, never expire, and route the runtime to the target registry without involving Wave. Stable image names take this form:

```console
your.registry.com/<image-path>:<checksum>
```

In this example:

- `<image-path>` is the path in your target registry, set in `wave.build.repository`. You choose the path.
- `<checksum>` is the same 16-character build identifier used by ephemeral URIs.

## Serving image layers

In the proxy lifecycle, Wave acts as an HTTP proxy during a pull. Wave delivers manifests itself. Layer blobs follow one of two paths.

Most public registries (Docker Hub, Quay.io, AWS ECR, Google Artifact Registry) host metadata themselves and offload binary storage to services such as AWS S3, AWS CloudFront, or Cloudflare. Wave returns HTTP redirects for layer requests. The runtime pulls the bytes directly from the storage service.

Self-hosted or custom registries sometimes serve layer binaries inline. When Wave fronts such a registry, it caches the binaries in object storage and serves them through a CDN. The hosted Wave service uses Cloudflare, the [same approach Docker Hub uses](https://www.cloudflare.com/case-studies/docker/).

The pass-through lifecycle has no proxy step. Stable URIs send the runtime to your target registry, which serves manifests and layers itself.

## Usage limits

The hosted `wave.seqera.io` service applies the following limits to API requests, builds, and image storage. Self-hosted deployments can configure their own values.

### API limits

Wave applies rate limits to every API request. Nextflow and the Wave CLI both call the Wave API on your behalf. A single pipeline run can consume many requests. Authenticating with a Seqera Platform access token is the recommended way to use Wave.

With a Seqera Platform access token, the following rate limits apply:

- 250 container builds per hour
- 2,000 container pulls per minute

Without a token, requests fall back to the anonymous limits:

- 25 container builds per day
- 100 container pulls per hour

These limits apply to the hosted `https://wave.seqera.io` service. Self-hosted Wave deployments configure their own limits and can disable anonymous access entirely.

:::note
Only the manifest request counts as a pull. Layer and blob fetches do not count. For example, a 100-layer image costs one pull.
:::

To authenticate, generate an access token in Seqera Platform and supply it to your Wave client. See [API limits](./api.md#api-limits) for more information.

### Memory limit

Wave builds use up to 3600 MiB (approximately 3.6 GB) of memory. Builds that exceed this limit fail.

### Time limits

Wave builds have a maximum build time of 15 minutes. Builds that exceed this limit are cancelled and fail. When you supply an access token and enable container freeze, the maximum build time increases to 25 minutes.

### Cache duration

Wave stores ephemeral images in the Cloudflare CDN for 90 days, after which it deletes them.

### Ephemeral token access

The lifetime of an ephemeral image that includes an access token in its URI depends on how the request was made:

- **Workflow-bound requests.** When a request originates from a Seqera Platform workflow run (Nextflow supplies the run's `workflowId`), Wave ties the token lifetime to the run instead of a fixed window. The token is granted a short time-to-live that Wave renews while the run is active, and lets it lapse shortly after the run completes (succeeds or fails). Two guarantees hold: the container becomes inaccessible no more than ~20 minutes (the `access-ttl`) after the run completes, and it is never accessible more than 48 hours (the `max-duration`) after the initial request, regardless of run state. The URI advertises the 48-hour hard ceiling as its expiration.

- **Other requests.** Builds, mirrors, and requests not bound to a workflow keep a fixed lifetime of 36 hours (the `cache.duration`) from the time the request is submitted.

System admins can revoke images before these limits. This behavior is controlled by the [`wave.tokens.*`](./configuration.md#general) settings and can be disabled with `wave.tokens.watcher.enabled: false`, in which case all requests use the fixed 36-hour lifetime.

### Context directory size limits

When you specify a context directory, the following file size limits apply:

- Each file must be no larger than 1 MB.
- Each directory must be no larger than 10 MB, including all files.

### Self-hosted limits

If you self-host Wave, you can configure limits in `config.yml`.
