---
title: Troubleshooting
description: Troubleshoot common Wave container service errors
date: "2026-07-07"
tags: [wave, containers, troubleshooting, registry]
---

## Registry access errors

### Error: `Container image does not exist or access is not authorized`

**Where you see it:** Seqera Platform shows `Invalid credentials for container registry` in the pipeline run error details. The underlying Wave API returns a response similar to:

```
POST https://wave.seqera.io/v1alpha2/container [400]
{"message":"Container image '<image>:<tag>' does not exist or access is not authorized"}
```

The Platform error calls this a credentials problem, but Wave returns this same 400 response for any failure reaching the registry, including network unreachability, not only bad credentials. The two most common root causes are:

- **Incorrect or missing registry credentials**: the image exists, but Wave cannot authenticate to pull it.
- **Registry not reachable from hosted Wave**: credentials are correct, but the registry is on a private network or behind a firewall that `wave.seqera.io` cannot reach from Seqera's cloud infrastructure.

#### Diagnose the cause

**Check credentials first.** In your Seqera workspace, select **Credentials** and verify the container registry credential:

1. Confirm the username, token, and registry hostname match the registry and image namespace you are pulling from.
2. Test the credential manually: run `docker login <registry>` with the same values to confirm they work from a machine that can reach the registry.

**Then check registry reachability.** If credentials are correct, the registry may not be reachable from hosted Wave. To confirm:

- Try pulling the image from a machine outside your organization's network (for example, a public cloud VM). If the pull fails there too, the registry is not publicly accessible.
- Any registry that is not publicly reachable cannot be accessed from hosted Wave, regardless of credentials. This includes a corporate artifact store, an on-premises registry, or a cloud registry locked to a private virtual private cloud (VPC).

#### Use self-hosted Wave for private registries

If your registry is not publicly accessible, deploy self-hosted Wave inside your own infrastructure, where it can reach the registry directly through your internal network. You do not need to change firewall rules, because Wave runs within your environment alongside the registry.

:::note
Hosted Wave (`wave.seqera.io`) supports only publicly accessible registries. For private or internal registries, use self-hosted Wave instead. See [Kubernetes installation](./install/kubernetes) to get started.
:::
