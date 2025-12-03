# Wave Lite

Wave Lite is a configuration mode for Wave containers that provides the following features:

- Container augmentation 
- Container inspection

Wave Lite enables the use of [Fusion file system](https://docs.seqera.io/fusion) in Nextflow pipelines on AWS, Azure, and GCP cloud deployments.

## Installation

- [Docker Compose](./install/docker-compose.md)
- [Kubernetes](./install/docker-compose.md)

:::info
Docker Compose installations only support Wave in Lite mode. Wave's full build capabilities require specific integrations with Kubernetes and AWS EFS Storage, making EKS and AWS a hard dependency for fully-featured deployments. After you have successfully deployed Wave Lite in Kubernetes, see [Configure Wave Build](./install/configure-wave-build.md) to extend your installation to support build capabilities. 
:::

## Limitations

The following Wave features are **not** available in Lite configuration:

- **Container Freeze**
- **Container Build service** 
- **Container Mirror service**
- **Container Security scanning**
- **Container blobs caching**
