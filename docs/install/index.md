---
title: Installation
description: Install Wave
tags: [docker compose, kubernetes, install, wave, wave lite]
---

Wave provides multiple installation and deployment options to accommodate different organizational needs and infrastructure requirements.

## Wave service by Seqera

Wave is Seqera's container provisioning service that streamlines container management for Nextflow data analysis pipelines. The simplest way to get started with Wave is
through the hosted Wave service at https://wave.seqera.io. This cloud-based option requires no local infrastructure setup and provides immediate access to Wave's container
provisioning capabilities. The hosted service is ideal for users who want to start using Wave quickly with minimal configuration overhead.

When using the hosted Wave service, you can add a simple configuration block to your `nextflow.config` file to integrate Wave with your Nextflow pipelines:

```groovy
wave {
    enabled = true
}

tower {
  accessToken = '<TOWER_ACCESS_TOKEN>'
}
```

You don't need a Seqera access token for basic Wave functionality. However, providing one grants you additional capabilities including access to private container
repositories, container freezing to your own registry, and bypassing rate limits on public registries. Container registry credentials should be configured using the [credentials manager](https://docs.seqera.io/platform-cloud/credentials/overview) in Seqera Platform.

If you're a Seqera Platform Enterprise customer, integrate Wave by adding the `TOWER_ENABLE_WAVE=true` and `WAVE_SERVER_URL="https://wave.seqera.io"` environment variables to your Seqera configuration environment. You must configure container registry credentials in the Platform UI to enable access to private repositories, and ensure the Wave service is accessible from your network infrastructure. For more information, see [Wave containers](https://docs.seqera.io/platform-enterprise/enterprise/configuration/wave).

## Self-hosted deployment

For organizations that require greater control over their container infrastructure or those with specific security and compliance requirements, Wave offers self-hosted deployment options.

### Docker Compose

Docker Compose provides a straightforward method for deploying Wave. Docker Compose packages Wave services and dependencies into a coordinated container stack and handles service orchestration automatically, managing startup and networking of Wave components.

Docker Compose installations support [Wave Lite](../wave-lite.md), a configuration mode for Wave that includes container augmentation and inspection capabilities, and enables the use of the Fusion file system in Nextflow pipelines. See [Install Wave Lite with Docker Compose](./docker-compose.md) for detailed configuration instructions.

### Kubernetes

Kubernetes is the preferred choice for deploying Wave in production environments that require scalability, high availability, and resilience. A Kubernetes installation involves setting up a cluster, configuring various components, and managing networking, storage, and resource allocation.

Kubernetes installations support [Wave Lite](../wave-lite.md), a configuration mode for Wave that supports container augmentation and inspection on AWS, Azure, and GCP deployments, and enables the use of Fusion file system in Nextflow pipelines. See [Install Wave Lite with Kubernetes](./kubernetes.md) for detailed configuration instructions.

If your organization requires full build capabilities, you can extend Wave beyond the base Wave Lite installation. This advanced configuration enables container
building functionality through specific integrations on Kubernetes and AWS EFS storage. See [Configure Wave build](./configure-wave-build.md) for detailed instructions.

<!--
## Installation Guides

import DocCardList from "@theme/DocCardList";

<DocCardList />
-->
