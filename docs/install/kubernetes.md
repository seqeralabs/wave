---
title: Kubernetes
description: Run Wave Lite on Kubernetes
tags: [kubernetes, install, wave, wave lite]
---

Wave enables you to provision container images on-demand, removing the need to build and upload them manually to a container registry. Wave can provision both disposable containers that are only accessible for a short period, and regular registry-persisted container images.

Kubernetes installations support Wave Lite, a configuration mode for Wave that supports container augmentation and inspection on AWS, Azure, and GCP deployments, and enables the use of Fusion file system in Nextflow pipelines. See [Wave Lite](../wave-lite.md) for more information.

This page describes how to run Wave Lite on Kubernetes. It includes:

- Database configuration
- Namespace creation
- Wave configuration
- Wave deployment
- Service creation
- Advanced configuration
- Next steps

Wave's full build capabilities require specific integrations on Kubernetes and AWS EFS Storage, making EKS and AWS a hard dependency for fully-featured deployments. After you configure a base Wave Lite installation on AWS with this guide, see [Configure Wave Build](./configure-wave-build.md) to optionally extend your installation to support build capabilities.

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

:::info[**Assumptions**]
This guide assumes:

- You have already deployed Seqera Platform Enterprise
- You will deploy Wave into the `wave` namespace
- You have appropriate cluster permissions to create namespaces, deployments, and services
- Your PostgreSQL and Redis instances are accessible from the Kubernetes cluster
:::

## Database configuration

Wave requires a PostgreSQL database to operate.

To create your `wave` PostgreSQL database and user account with appropriate privileges:

1. Connect to PostgreSQL.
1. Create a dedicated user for Wave:

    ```sql
    CREATE ROLE wave_user LOGIN PASSWORD '<SECURE_PASSWORD>';
    ```

    Replace `<SECURE_PASSWORD>` with a secure password for the database user.

1. Create the Wave database:

    ```sql
    CREATE DATABASE wave;
    ```

1. Connect to the wave database:

    ```sql
    \c wave;
    ```

1. Grant basic schema access:

    ```sql
    GRANT USAGE, CREATE ON SCHEMA public TO wave_user;
    ```

1. Grant privileges on existing tables and sequences:

    ```sql
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO wave_user;
    GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO wave_user;
    ```

1. Grant privileges on future tables and sequences:

    ```sql
    ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wave_user;

    ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO wave_user;
    ```

## Namespace creation

A Kubernetes namespace provides logical isolation for cluster resources, allowing you to organize all Wave components together with proper resource limits and access controls.

To create a `wave` namespace:

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

2. Deploy the `wave` namespace:

    ```bash
    kubectl apply -f namespace.yaml
    ```

## Wave configuration

Wave requires configuration for database connections, Redis caching, and server settings.

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
    env:
    - name: WAVE_DB_PASSWORD
        valueFrom:
        secretKeyRef:
            name: wave-secrets
            key: db-password
    ```

    See the Kubernetes [Secrets](https://kubernetes.io/docs/concepts/configuration/secret/) for more details.
    :::

    2. Deploy the ConfigMap to the `wave` namespace:

    ```bash
    kubectl apply -f wave-config.yaml
    ```

## Wave deployment

Deploy Wave using the following deployment manifest:

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

2. Deploy Wave to the `wave` namespace:

    ```bash
    kubectl apply -f wave-deployment.yaml
    ```

## Service creation

To expose Wave within the cluster using a Service:

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

2. Deploy the service to the `wave` namespace:

    ```bash
    kubectl apply -f wave-service.yaml
    ```

## Next steps

### Configure Seqera Platform to integrate with Wave

Configure your Seqera Platform Enterprise deployment to integrate with Wave by setting the Wave server endpoint in your `tower.yml` [configuration](https://docs.seqera.io/platform-enterprise/latest/enterprise/configuration/wave).

### Networking

Wave must be accessible from:

- Seqera Platform services
- Compute environments (for container image access)

Configure external access using a Kubernetes ingress.

Update the following example ingress with your provider-specific annotations:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: wave-ingress
  namespace: wave
spec:
  rules:
  - host: wave.your-domain.com
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

### TLS

Wave does not handle TLS termination directly. Configure TLS at your ingress controller or load balancer level. Most ingress controllers support automatic certificate provisioning through provider integrations.


### Production environments

Consider implementing the following for production deployments:

**Reliability:**
- Pod Disruption Budgets, for availability during cluster maintenance
- Horizontal Pod Autoscaler, for automatic scaling based on load
- Multiple replicas with anti-affinity rules, for high availability

**Resource management:**
- Node selectors or affinity rules for optimal pod placement
- Resource quotas and limit ranges for the Wave namespace

### AWS credentials to access ECR

Wave requires access to AWS ECR for container image management. Create an IAM role with the following permissions:

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

## Advanced configuration

See [Configuring Wave](./configure-wave.md) for advanced Wave features, scaling guidance, and integration options.
