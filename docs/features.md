---
title: Features
description: Describe the main features of Wave container service.
hide:
  - toc
---

# Features

<div markdown class="grid">
<div markdown>

### Conda based containers

Package management systems such as Conda and Bionconda simplify the installation of scientific software. However, there’s considerable friction when it comes to using those tools to deploy pipelines in cloud environments.
Wave enables dynamic provisioning of container images from any Conda or Bioconda recipe. Just declare the Conda packages in your Nextflow pipeline and Wave will assemble the required container.

</div>
<div markdown>

### Augment existing containers

Regulatory and security requirements sometimes dictate specific container images, but additional context is often needed.
Wave enables any existing container to be extended without rebuilding it. Developers can add user-provided content such as custom scripts and logging agents, providing greater flexibility in the container’s configuration.

</div>
<div markdown>

### Access private container registries

Container registry authentication is the new norm. Yet when it comes to authenticating against cloud-specific container registries, the process is hardly hassle free.
Wave integrates with Tower Cloud credentials management enabling seamless access and publishing to private registries.

</div>
<div markdown>

### Deploying containers across multi-clouds

Cloud vendors provide integrated container registries, providing better performance and cost-efficiency than central, remote registries.
This requires mirroring container collections across multiple accounts, regions, and cloud providers when deploying multi-cloud pipelines.
Wave streamlines this process by provisioning the required containers to the target registry on-demand during the pipeline executions.

</div>
<div markdown>

### Optimize workloads for specific architectures

Modern data pipelines can be deployed across different data centers having different hardware architectures. e.g., amd64, arm64, and others. This requires curating different collections of containers for each architecture.
Wave allows for the on-demand provisioning of containers, depending on the target execution platform (in development).

</div>
<div markdown>

### Near caching

The deployment of production pipelines at scale can require the use of multiple cloud regions to enable efficient resource allocation.
However, this can result in an increased overhead when pulling container images from a central container registry. Wave allows the transparent caching of container images in the same region where computation occurs, reducing data transfer costs and time (in development).

</div>
</div>
