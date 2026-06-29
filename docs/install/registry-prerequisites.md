---
title: Registry prerequisites
description: Per-registry pre-creation rules for the registries Wave pushes to and pulls from.
---

These registries are the targets Wave pushes built and mirrored images to, and pulls private images from during augmentation. They are independent of where Wave runs: builds run on Amazon EKS, but the push and pull targets can be any registry you configure.

Before Wave pushes built or mirrored images to a registry, confirm whether that registry requires the target repository path to exist beforehand. Wave relies on the underlying push tool (BuildKit for builds, Skopeo for mirrors). If the registry rejects the push because the path does not exist, the build fails partway through.

For credential syntax, see [Configuration](../configure-wave.md). For common push and authentication failures, see [Registry push and authentication failures](../troubleshoot.md#registry-push-and-authentication-failures).

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
