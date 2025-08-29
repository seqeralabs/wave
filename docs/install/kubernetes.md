---
title: Install Wave Lite with Kubernetes
description: Install Wave Lite on Kubernetes
tags: [kubernetes, install, wave, wave lite]
---

Kubernetes is the preferred choice for deploying Wave in production environments that require scalability, high availability, and resilience. Its installation involves setting up a cluster, configuring various components, and managing networking, storage, and resource allocation.

Kubernetes installations support [Wave Lite](../wave-lite.md), a configuration mode for Wave that supports container augmentation and inspection on AWS, Azure, and GCP deployments, and enables the use of Fusion file system in Nextflow pipelines.

This page describes how to run Wave Lite on Kubernetes. It includes steps to:

- Configure a database
- Create a namespace
- Configure Wave
- Deploy Wave
- Create a service
- Configure Wave connectivity

Wave's full build capabilities require specific integrations with Kubernetes and AWS Elastic File System (EFS) Storage, making Amazon Elastic Kubernetes Service (EKS) and AWS hard dependencies for full-featured deployments. If you require build capabilities, see [Configure Wave build](./configure-wave-build.md) to extend your installation.

:::info[**Prerequisites**]
You will need the following to get started:

- A Kubernetes cluster (version 1.31 or higher)
- An externally managed PostgreSQL instance (version 12 or higher)
- An externally managed Redis instance (version 6.0 or higher)
- A Kubernetes cluster with at least:
    - **Memory**: 4 GB RAM per Wave pod
    - **CPU**: 1 CPU core per pod
    - **Network**: Connectivity to your external PostgreSQL and Redis instances
    - **Storage**: Sufficient storage for your container images and temporary files
:::

:::warning[**Assumptions**]
This guide assumes:

- You have already deployed Seqera Platform Enterprise
- You will deploy Wave into the `wave` namespace
- You have appropriate cluster permissions to create namespaces, deployments, and services
- Your PostgreSQL and Redis instances are accessible from the Kubernetes cluster
:::

## Configure a database

To create a PostgreSQL database and user account, follow these steps:

1. Connect to PostgreSQL.
1. Create a dedicated `wave` database and user account with the appropriate privileges:

    ```sql
    CREATE ROLE wave_user LOGIN PASSWORD '<SECURE_PASSWORD>';

    CREATE DATABASE wave;

    \c wave;

    GRANT USAGE, CREATE ON SCHEMA public TO wave_user;

    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO wave_user;
    GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO wave_user;

    ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wave_user;

    ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO wave_user;
    ```

    Replace `<SECURE_PASSWORD>` with a secure password for the database user.

## Create a namespace

To create a Kubernetes `wave` namespace, follow these steps:

1. Create `namespace.yaml` with the following configuration:

    ```yaml
    ---
    apiVersion: v1
    kind: Namespace
    metadata:
      name: "wave"
      labels:
        app: wave-app
    ```

1. Apply the `wave` namespace configuration:

    ```bash
    kubectl apply -f namespace.yaml
    ```

## Configure Wave

To configure database connections, Redis caching, and server settings, follow these steps:

1. Create `wave-config.yaml` with the following configuration:

    ```yaml
    ---
    apiVersion: v1
    kind: ConfigMap
    metadata:
      name: wave-cfg
      namespace: "wave"
    data:
      config.yml: |
        wave:
          # Build service configuration - disabled for Wave base installation
          build:
            enabled: false
          # Mirror service configuration - disabled for Wave base installation
          mirror:
            enabled: false
          # Security scanning configuration - disabled for Wave base installation
          scan:
            enabled: false
          # Blob caching configuration - disabled for Wave base installation
          blobCache:
            enabled: false
          # Database connection settings
          db:
            uri: "jdbc:postgresql://<POSTGRES_HOST>:5432/wave"
            user: "wave_user"
            password: "<SECURE_PASSWORD>"

          # Redis configuration for caching and session management
          redis:
            uri: "rediss://<REDIS_HOST>:6379"

          # Platform integration (optional)
          tower:
            endpoint:
              url: "<PLATFORM_SERVER>"

          # Micronaut framework configuration
          micronaut:
            # Executor configuration for handling concurrent requests
            executors:
              stream-executor:
                type: FIXED
                number-of-threads: 16
            # Netty HTTP server configuration
            netty:
              event-loops:
                default:
                  num-threads: 64
                stream-pool:
                  executor: stream-executor
            # HTTP client configuration
            http:
              services:
                stream-client:
                  read-timeout: 30s
                  read-idle-timeout: 5m
                  event-loop-group: stream-pool

        # Management endpoints configuration
        loggers:
        # Enable metrics for monitoring
        metrics:
          enabled: true
        # Enable health checks
        health:
          enabled: true
          disk-space:
            enabled: false
          jdbc:
            enabled: false
    ```

    Replace the following:

    - `<POSTGRES_HOST>`: your Postgres service endpoint
    - `<REDIS_HOST>`: your Redis service endpoint
    - `<SECURE_PASSWORD>`: your secure password for the database user
    - `<PLATFORM_SERVER>`: your Platform endpoint URL (_optional_)

    :::tip
    It is recommended to use Kubernetes Secrets for sensitive data instead of embedding them in the ConfigMap. For example, by adding environment variables with secrets to your Wave deployment manifest:

    ```yaml
    ---
    env:
    - name: WAVE_DB_PASSWORD
        valueFrom:
        secretKeyRef:
            name: wave-secrets
            key: db-password
    ```

    For more information, see Kubernetes [Secrets](https://kubernetes.io/docs/concepts/configuration/secret/).
    :::

1. Apply the ConfigMap:

    ```bash
    kubectl apply -f wave-config.yaml
    ```

## Deploy Wave

To deploy Wave using the deployment manifest, follow these steps:

1. Create `wave-deployment.yaml` with the following configuration:

    ```yaml
    ---
    apiVersion: apps/v1
    kind: Deployment
    metadata:
      name: wave
      namespace: "wave"
      labels:
        app: wave-app
    spec:
      replicas: 1
      selector:
        matchLabels:
          app: wave-app
      template:
        metadata:
          labels:
            app: wave-app
        spec:
          containers:
            - image: <AWS_ACCOUNT>.dkr.ecr.us-east-1.amazonaws.com/nf-tower-enterprise/wave:<WAVE_IMAGE_TAG>
              name: wave-app
              ports:
                - containerPort: 9090
              env:
                - name: MICRONAUT_ENVIRONMENTS
                  value: "postgres,redis,lite"
              resources:
                requests:
                  memory: "4000Mi"
                limits:
                  memory: "4000Mi"
              workingDir: "/work"
              volumeMounts:
                - name: wave-cfg
                  mountPath: /work/config.yml
                  subPath: "config.yml"
              readinessProbe:
                httpGet:
                  path: /health
                  port: 9090
                initialDelaySeconds: 5
                timeoutSeconds: 3
              livenessProbe:
                httpGet:
                  path: /health
                  port: 9090
                initialDelaySeconds: 5
                timeoutSeconds: 3
                failureThreshold: 10
          volumes:
            - name: wave-cfg
              configMap:
                name: wave-cfg
          restartPolicy: Always
    ```

    Replace the following:

    - `<AWS_ACCOUNT>`: your AWS account ID
    - `<WAVE_IMAGE_TAG>`: your Wave image tag version

2. Apply the Wave configuration:

    ```bash
    kubectl apply -f wave-deployment.yaml
    ```

## Create a service

To expose Wave within the cluster using a Service, follow these steps:

1. Create `wave-service.yaml` with the following configuration:

    ```yaml
    ---
    apiVersion: v1
    kind: Service
    metadata:
      name: wave-service
      namespace: "wave"
      labels:
        app: wave-app
    spec:
      selector:
        app: wave-app
      ports:
        - name: http
          port: 9090
          targetPort: 9090
          protocol: TCP
      type: ClusterIP
    ```

1. Apply the service to the namespace:

    ```bash
    kubectl apply -f wave-service.yaml
    ```

## Configure Wave connectivity

After deploying Wave Lite, configure networking and integrate with Seqera Platform to make Wave accessible and functional.

### Set up networking

Wave must be accessible from:

- Seqera Platform services
- Compute environments (for container image access)

Use a Kubernetes ingress to configure external access.

To set up an ingress, follow these steps:

1. Create `wave-ingress.yaml` with the following configuration:

    ```yaml
    apiVersion: networking.k8s.io/v1
    kind: Ingress
    metadata:
      name: wave-ingress
      namespace: wave
      # Add your ingress controller annotations here
    spec:
      rules:
      - host: <WAVE_DOMAIN>
        http:
          paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: wave-service
                port:
                  number: 9090
    ```

    Replace `<WAVE_DOMAIN>` with your provider-specific domain.

2. Apply the ingress:

    ```bash
    kubectl apply -f wave-ingress.yaml
    ```

### Configure TLS

Wave does not handle TLS termination directly.
Configure it at your ingress controller or load balancer level.
Most ingress controllers support automatic certificate provisioning.

### Connect to Seqera Platform

Configure your Seqera Platform deployment to use Wave by setting the Wave server endpoint in your `tower.yml` configuration. For more information, see [Pair your Seqera instance with Wave](https://docs.seqera.io/platform-enterprise/enterprise/configuration/wave#pair-your-seqera-instance-with-wave).

### Configure AWS ECR access

Wave needs specific permissions to manage container images.

To configure AWS ECR access:

- Create an IAM role with the following permissions:

    ```json
    "Statement": [
        {
            "Action": [
                "ecr:BatchCheckLayerAvailability",
                "ecr:GetDownloadUrlForLayer",
                "ecr:GetRepositoryPolicy",
                "ecr:DescribeRepositories",
                "ecr:ListImages",
                "ecr:DescribeImages",
                "ecr:BatchGetImage",
                "ecr:GetLifecyclePolicy",
                "ecr:GetLifecyclePolicyPreview",
                "ecr:ListTagsForResource",
                "ecr:DescribeImageScanFindings",
                "ecr:CompleteLayerUpload",
                "ecr:UploadLayerPart",
                "ecr:InitiateLayerUpload",
                "ecr:PutImage"
            ],
            "Effect": "Allow",
            "Resource": [
                "<RESOURCE_NAME>/wave/*"
            ]
        }
    ]
    ```

    Replace `<RESOURCE_NAME>` with your ECR repository Amazon Resource Name (ARN).

## Optimize for production environments

For production deployments, implement these reliability and performance improvements:

**High availability:**

- Set `replicas: 3` or higher in your deployment
- Add pod anti-affinity rules to distribute replicas across nodes
- Configure Pod Disruption Budgets for maintenance windows

**Auto-scaling:**

- Set up Horizontal Pod Autoscaler based on CPU/memory usage
- Configure resource quotas and limits for the Wave namespace

**Monitoring:**

- Set up monitoring for Wave pods and services
- Configure alerting for health check failures
- Monitor database and Redis connection health

## Configure advanced options

For advanced Wave features, scaling guidance, and integration options, see [Configure Wave](./configure-wave.md).
