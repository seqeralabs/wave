---
title: Registry prerequisites
description: Per-registry pre-creation rules and failure modes for the registries Wave pushes to and pulls from.
---

These registries are the targets Wave pushes built and mirrored images to, and pulls private images from during augmentation. They are independent of where Wave runs: builds run on Amazon EKS, but the push and pull targets can be any registry you configure.

Before Wave pushes built or mirrored images to a registry, confirm whether that registry requires the target repository path to exist beforehand. Wave relies on the underlying push tool (BuildKit for builds, Skopeo for mirrors). If the registry rejects the push because the path does not exist, the build fails partway through.

For credential syntax, see [Configure Wave](../configure-wave.md). For the identity model behind registry access, see [Identity and credentials](identity-and-credentials.md).

## Pre-creation behavior by registry

| Registry | Pre-create required? | Notes |
| --- | --- | --- |
| **AWS ECR** | Yes (by default) | Each repository must exist before push. Create with `aws ecr create-repository` or your IaC tooling. AWS offers registry-level auto-create policies, but they are not enabled by default. |
| **Docker Hub** | No | Repositories auto-create in your user or organization namespace on first push. Repo-count and pull rate limits apply on paid tiers. |
| **GitHub Container Registry (GHCR)** | No | Auto-created under your user or org namespace. Default visibility inherits from the org's package settings unless overridden. |
| **Google Artifact Registry** | Partial | The **repository** (the GCP namespace container) must be pre-created with `gcloud artifacts repositories create`. Image paths within it auto-create on push. |
| **Google Container Registry (legacy GCR)** | No | Auto-creates on push. GCR is being phased out. Target Artifact Registry for new deployments. |
| **Azure Container Registry (ACR)** | No | Image paths auto-create within an existing ACR instance. The instance must exist and Wave must hold the `AcrPush` role on it. |
| **Harbor** | Project: Yes / Image: No | Harbor **projects** must be pre-created (UI or API). Images within a project auto-create if project policies permit. |

## Failure modes

When a push target is misconfigured, the failure usually appears as one of the following:

1. **No credentials match the target host.** Wave returns an authentication error at token-request time, before BuildKit or Skopeo launches. This is the fastest failure to diagnose.

2. **The repository does not exist and the registry requires pre-creation.** The push fails with `403 Forbidden` or `404 Not Found` partway through the layer upload, often after an initial `HEAD` succeeds but the final manifest `PUT` fails.

3. **Credentials exist but lack push scope.** Similar symptom to (2). The push typically returns a `403` on the final manifest `PUT` even though layer uploads appear to work. Check the credential's scope for `push`, `write`, or `deploy` permission.

4. **Repository key missing from the path (JFrog).** The push fails with `404`. Ensure the repository key is the first path segment after the host, for example `artifactory.example.com/docker-local/...`.

5. **The registry exists but Wave's cloud identity targets the wrong region or account.** For ECR in particular, a misaligned `aws.region` or jump-role configuration produces STS `AccessDenied` errors in the BuildKit pod logs.
