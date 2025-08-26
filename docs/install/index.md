---
title: Installation
description: Install self-hosted Wave
tags: [docker compose, kubernetes, install, wave, wave lite]
---

import DocCardList from "@theme/DocCardList";

Wave provides multiple installation and deployment options to accommodate different organizational needs and infrastructure requirements.

## Hosted service

The simplest way to get started with Wave is through the hosted service at [Seqera containers](https://seqera.io/containers/). This cloud-based option requires no local infrastructure setup and provides immediate access to Wave's container provisioning capabilities. Seqera containers is ideal for users who want to start using Wave quickly with minimal configuration overhead.

When using the Seqera containers service, you can add a simple configuration block to your `nextflow.config` file to integrate Wave with your Nextflow pipelines:

```groovy
wave {
    enabled = true
}
```

For access to other public or private container repositories, you can add container registry credentials to your `nextflow.config` file. See the [Nextflow and Wave](../tutorials/nextflow-wave) tutorial to get started.

You can also integrate your registry with Seqera Platform's credential management system. See [Credentials overview](https://docs.seqera.io/platform-cloud/credentials/overview) for more information.

## Self-hosted deployment

For organizations that require greater control over their container infrastructure or those with specific security and compliance requirements, Wave offers self-hosted deployment options.

### Docker Compose

Docker Compose provides a straightforward deployment method suitable for development environments, testing, and production deployments. This method packages Wave services and dependencies into a coordinated set of containers that can be managed as a single application stack.

The Docker Compose deployment handles service orchestration automatically, coordinating the startup and networking of Wave components. Updates can be performed by downloading new container versions and restarting the application stack. To install with Docker Compose, see [Docker Compose installation](./docker-compose.md).

### Kubernetes

Kubernetes deployment offers enterprise-grade scalability and high availability for production environments. Wave can also use the k8s pod to delegate the transfer task for scalability and includes comprehensive configuration options for build processes.

<DocCardList />
