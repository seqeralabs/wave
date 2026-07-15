---
title: Self-hosted Wave
description: When to self-host Wave, the two self-hosted configurations, and how to choose an install path.
---

Self-hosting runs the Wave container-provisioning service inside your own infrastructure instead of using Seqera-hosted Wave. Teams self-host when they cannot grant Seqera-hosted Wave access to a private registry, or when compliance requires the service to run inside their own boundary.

You can self-host Wave in two configurations:

- **Wave Lite**: Container augmentation, inspection, and private registry authentication.
- **Wave**: Everything in Wave Lite, plus on-demand container builds, freeze, mirroring, and security scanning.

Wave builds on Wave Lite. The full configuration is a Wave Lite deployment with build, mirror, and scan added. To run it, install Wave Lite on an Amazon EKS cluster, then enable those features.

For the full capability comparison, see the [feature matrix](../features/index.mdx). For how Wave provisions containers, see [How Wave works](../how-wave-works.md).

## Choose an install path

Every deployment starts with Wave Lite. Choose the path that matches your infrastructure and whether you need the full Wave configuration.

### Docker Compose

Run Wave Lite on a single Docker host without Kubernetes. Choose this path for a compliance-constrained site that cannot run Amazon EKS, or a deployment too small to need a cluster. Docker Compose runs Wave Lite only. The full configuration requires EKS.

[Install Wave Lite with Docker Compose](docker-compose.md)

### Kubernetes

Run Wave Lite on a Kubernetes cluster you already operate. This is also the path to the full Wave configuration. Install Wave Lite on Amazon EKS, then [enable Wave builds](aws-build.md) to add on-demand builds, freeze, mirroring, and scanning. Wave Lite has no AWS dependency and runs on any conformant Kubernetes distribution. Only the full configuration requires EKS.

You can deploy with raw manifests, or with the official [Seqera Wave Helm chart](https://artifacthub.io/packages/helm/seqera/wave) alongside the deployment notes in the Kubernetes guide.

[Install Wave Lite on Kubernetes](kubernetes-lite.md)
