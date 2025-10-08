---
title: Kubernetes installation
---

Wave enables you to provision container images on-demand, removing the need to build and upload them manually to a container registry. Wave can can provision both disposable containers that are only accessible for a short period, and regular registry-persisted container images.

This installation guide covers Wave in [Lite](../wave-lite.md) mode. Wave Lite provides container augmentation and inspection capabilities on AWS, Azure, and GCP cloud deployments, and enables the use of Fusion file system in Nextflow pipelines.

:::info
Wave's full build capabilities require specific integrations with Kubernetes and AWS EFS Storage, making EKS and AWS a hard dependency for fully-featured deployments. After you have configured a base Wave Lite installation on AWS with this guide, see [Configure Wave Build](./configure-wave-build.md) to extend your installation to support build capabilities.
:::

## Prerequisites

**Required infrastructure:**
- **Kubernetes cluster** - Version 1.31 or higher (any distribution)
- **PostgreSQL instance** - Version 12 or higher (managed externally)
- **Redis instance** - Version 6.0 or higher (managed externally)

## System requirements

The minimum system requirements for a Wave Kubernetes installation are:

- **Memory**: Minimum 4GB RAM per Wave pod
- **CPU**: Minimum 1 CPU core per pod
- **Network**: Connectivity to your external PostgreSQL and Redis instances
- **Storage**: Sufficient storage for your container images and temporary files

:::info
See [Configure Wave](../configure-wave.md) for detailed scaling and performance tuning guidance.
:::

## Assumptions

This guide assumes:
- You have already deployed Seqera Platform Enterprise
- You will deploy Wave into the `wave` namespace
- You have appropriate cluster permissions to create namespaces, deployments, and services
- Your PostgreSQL and Redis instances are accessible from the Kubernetes cluster

## Database configuration

Wave requires a PostgreSQL database to operate.

Create a dedicated `wave` database and user account with the appropriate privileges:

```sql
-- Create a dedicated user for Wave
CREATE ROLE wave_user LOGIN PASSWORD 'your_secure_password';

-- Create the Wave database
CREATE DATABASE wave;

-- Connect to the wave database
\c wave;

-- Grant basic schema access
GRANT USAGE, CREATE ON SCHEMA public TO wave_user;

-- Grant privileges on existing tables and sequences
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO wave_user;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO wave_user;

-- Grant privileges on future tables and sequences
ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wave_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO wave_user;
```

## Create namespace

```yaml
---
apiVersion: v1
kind: Namespace
metadata:
  name: "wave"
  labels:
    app: wave-app
```

## Configure Wave

Create a ConfigMap containing Wave's configuration. Update the following values to match your environment:

- Database connection details (`uri`, `user`, `password`)
- Redis connection string
- Seqera Platform API endpoint

:::warning
This configuration contains sensitive values. It is recommended to use Kubernetes Secrets for sensitive data, instead of embedding them directly in the ConfigMap. See the [Kubernetes Secrets documentation](https://kubernetes.io/docs/concepts/configuration/secret/) for more details.

Example using environment variables with secrets:

```yaml
env:
  - name: WAVE_DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: wave-secrets
        key: db-password
```
:::

```yaml
kind: ConfigMap
apiVersion: v1
metadata:
  name: wave-cfg
  namespace: "wave"
  labels:
    app: wave-cfg
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
        uri: "jdbc:postgresql://your-postgres-host:5432/wave"
        user: "wave_user"
        password: "your_secure_password"

    # Redis configuration for caching and session management
    redis:
      uri: "rediss://your-redis-host:6379"

    # Platform integration (optional)
    tower:
      endpoint:
        url: "https://your-platform-server.com"

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

## Create deployment

Deploy Wave using the following Deployment manifest:

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
        - image: REPLACE_ME_AWS_ACCOUNT.dkr.ecr.us-east-1.amazonaws.com/nf-tower-enterprise/wave:REPLACE_ME_WAVE_IMAGE_TAG
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

## Create Service

Expose Wave within the cluster using a Service:

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
                "<REPO>/wave/*"
            ]
        }
  ```

### Advanced configuration

See [Configure Wave](../configure-wave.md) for advanced Wave features, scaling guidance, and integration options.
