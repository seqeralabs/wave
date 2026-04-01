---
title: Troubleshooting Wave build errors
description: Troubleshoot Wave build errors that originate from container registries behind reverse proxies such as Cloudflare
date created: 2026-04-01
date edited: 2026-04-01
tags: [wave, buildkit, troubleshooting, cloudflare, proxied-registries]
---

# Troubleshooting Wave build errors

When building containers with Wave, you might encounter the following issues.

## Get more detail from build logs

The Wave build page shows a single-line summary of the BuildKit error. To see the full build log:

1. Open the Wave build report link in the Wave UI.
2. Review the complete log output — the layer identifier (for example, `sha256:8d2d10...`) and the target URL in the error message are the key clues for identifying the failure point.
3. Look at the HTTP status code returned in the failed `PUT` or `POST` request to determine whether the error originated in your registry infrastructure, a proxy layer, or the Wave service itself.

:::note
The current Wave build page displays only a single-line error message from BuildKit. This can make it difficult to distinguish infrastructure errors from Wave service issues without inspecting the full build log.
:::

## Build errors in proxied environments

If your build repository (`wave.build.repository`) is behind a reverse proxy, build errors may originate from the proxy rather than Wave or BuildKit. Wave orchestrates BuildKit, which pushes layers to your configured repository. If your registry is behind a reverse proxy, the proxy receives the request first. If the backend times out or returns an unexpected response, the proxy returns its own HTTP error code to BuildKit, which surfaces it in the build log. The Wave build page displays only that single-line BuildKit error. The origin of the failure is not visible in the UI.

:::note
Errors that appear to be Wave or BuildKit failures may actually originate in your registry infrastructure or network.
:::

Use the following signals to determine where the error originated:

| Signal | Likely source |
|---|---|
| Error on `PUT` or `POST` to your build repository | Your registry or reverse proxy |
| Error on `FROM` or base image pull | Source image registry (e.g., Docker Hub, ECR) |
| Build fails consistently for large images only | Proxy timeout on large blob upload |
| Build fails intermittently across pipelines | Registry rate limit, quota, or transient proxy issue |
| URL in error contains `wave.seqera.io` | Potential Wave service issue — contact Seqera support |

### Cloudflare-proxied registries

If your registry hostname resolves through Cloudflare, BuildKit push requests pass through the Cloudflare CDN before reaching your registry backend. Cloudflare returns its own HTTP error codes when it cannot successfully proxy a request, which appear in the Wave build log as the status code in a failed `PUT` or `POST` request.

#### Detect Cloudflare in the path

Before troubleshooting, confirm whether your registry hostname resolves through Cloudflare:

```bash
dig <your-registry-hostname> +short
```

If the output includes a CNAME ending in `.cdn.cloudflare.net`, or IP addresses in the Cloudflare ranges (e.g., `104.x.x.x` or `172.64.x.x`),
Cloudflare is in the path between Wave/BuildKit and your registry.

**Example output for a registry behind Cloudflare:**

```bash
dig <your-registry-hostname> +short
<your-registry-hostname>.cdn.cloudflare.net.
104.18.x.x
172.64.x.x
```

Use the following signals to determine which Cloudflare error you are seeing:

| HTTP code | Likely cause |
|---|---|
| `520` | Your registry returned an invalid or unexpected response to Cloudflare |
| `522` | Cloudflare could not establish a connection to your registry backend |
| `524` | Your registry accepted the connection but did not respond before Cloudflare's proxy timeout expired |

#### Error: `520` on blob upload

You might see an error like the following in your Wave build log:

```plaintext
error: failed to copy: unexpected status from PUT request to
https://<your-registry>/v2/<namespace>/blobs/uploads/<id>: 520 <none>
```

This issue occurs when Cloudflare receives an invalid or unexpected response from your registry while proxying a chunked `PUT` upload. A common root cause is a large image layer timing out on the registry side while Cloudflare waits for a response.

:::note
`520 <none>` is a Cloudflare-specific error. It indicates Cloudflare received an unexpected response from your registry backend. This is not a Wave or BuildKit bug.
:::

To resolve this issue:

- Use a build repository that is not behind a reverse proxy (for example, a direct ECR or GCR endpoint) to confirm whether the proxy is the cause.
- Split large Conda environments into smaller images to reduce individual layer sizes.

#### Error: `522` on blob upload

You might see a `522` error in your Wave build log when pushing to a Cloudflare-proxied registry.

This issue occurs when Cloudflare cannot establish or complete a connection to your registry backend. Cloudflare returns a `522` if the origin server does not return a SYN+ACK before the connection is established, or does not acknowledge Cloudflare's request after the connection is established.

Common causes include your registry being overloaded or offline, or firewall rules blocking Cloudflare's IP ranges from reaching the registry backend.

To resolve this issue:

- Ask your network or platform team to verify that your registry backend is reachable from Cloudflare's IP ranges and that no firewall rules are blocking the connection.
- Use a build repository that is not behind a reverse proxy (for example, a direct ECR or GCR endpoint) to confirm whether the proxy is the cause.

#### Error: `524` on blob upload

You might see a `524` error in your Wave build log when pushing to a Cloudflare-proxied registry.

This issue occurs when your registry successfully accepts the connection from Cloudflare but does not send a response before Cloudflare's proxy timeout expires. This is common when pushing large image layers.

To resolve this issue:

- Split large Conda environments into smaller images to reduce individual layer sizes.
