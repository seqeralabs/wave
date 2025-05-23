---
title: Kubernetes Installation
---

Wave allows provisioning container images on-demand, removing the need to build and upload them manually to a container registry. Containers provisioned by Wave can be both disposable (ephemeral containers only accessible for a short period) and regular long-term registry-persisted container images.

This installation guide covers Wave in **augmentation-only mode** (commonly referred to as "wave-lite") which provides:

- **inspect** - Container inspection and metadata retrieval
- **augment** - Container image augmentation with additional layers

Wave's full build capabilities require specific integrations with Kubernetes and AWS EFS Storage, making EKS & AWS a hard dependency for fully-featured deployments. The following features will **not** work in this configuration:

- **Container Freeze**
- **Container Build service** 
- **Container Mirror service**
- **Container Security scanning**
- **Container blobs caching**

See [Configuring Wave Build](../configuring-wave-build.md) for details on extending your installation to support build capabilities once you have configured a base installation by following this guide.
 
## Prerequesites

**Required Infrastructure:**
- **Kubernetes cluster** - Version 1.20+ (any distribution)
- **PostgreSQL instance** - Version 12+ (managed externally)
- **Redis instance** - Version 6.0+ (managed externally)

## System Requirements

The following system requirements are recommended for a Wave Kubernetes installation:

- **Memory**: Minimum 4GB RAM per Wave pod
- **CPU**: Minimum 1 CPU core per pod 
- **Network**: Connectivity to external PostgreSQL and Redis instances
- **Storage**: Sufficient storage for container images and temporary files

For detailed scaling and performance tuning guidance, see [Configuring Wave](../configuring-wave.md).

## Assumptions

This guide assumes:
- You have already deployed Seqera Platform
- You will deploy Wave into the `wave` namespace
- You have appropriate cluster permissions to create namespaces, deployments, and services
- Your PostgreSQL and Redis instances are accessible from the Kubernetes cluster


## Database configuration

Wave requires a PostgreSQL database to operate. Create a dedicated `wave` database and user account with the appropriate privileges.

If neccesary execute the following SQL commands on your PostgreSQL instance:

```sql
-- Create a dedicated user for Wave
CREATE ROLE wave_user LOGIN PASSWORD 'your_secure_password_here';

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
This configuration contains sensitive values. It is recommended to use Kubernetes Secrets for sensitive data instead of embedding them directly in the ConfigMap. See the [Kubernetes Secrets documentation](https://kubernetes.io/docs/concepts/configuration/secret/) for more details.

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
  config.yml:
    wave:
      build:
        enabled: false
      mirror:
        enabled: false
      scan:
        enabled: false
      blobCache:
        enabled: false
      db:
        uri: "jdbc:postgresql://localhost:5432/wave"
        user: "postgres"
        password: "postgres"
    redis:
      uri: "rediss://REPLACE_ME_WAVE_REDIS_URL:6379"
    tower:
      endpoint:
        url: REPLACE_ME_TOWER_SERVER_URL
    micronaut:
      executors:
        stream-executor:
          type: FIXED
          number-of-threads: 16
      netty:
        event-loops:
          default:
            num-threads: 64
          stream-pool:
            executor: stream-executor
      http:
        services:
          stream-client:
            read-timeout: 30s
            read-idle-timeout: 5m
            event-loop-group: stream-pool
    loggers:
      env:
        enabled: false
      bean:
        enabled: false
      caches:
        enabled: false
      refresh:
        enabled: false
      loggers:
        enabled: false
      info:
        enabled: false
      metrics:
        enabled: true
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
            - name: WAVE_JVM_OPTS
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


## Next Steps


### Configuring Wave Platform to integrate with Wave

Configure your Seqera Platform deployment to integrate with Wave by setting the Wave server endpoint in your Platform configuration.


### Networking.

Wave needs to be accessible from:

- Seqera Platform services
- Compute environments (for container image access)

Configure external access using a kubernetes ingress, the following is an example ingress which will require updating with provider specific annotations.


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


### Production Environments

Consider implementing the following for production deployments:

**Reliability:**
- Pod Disruption Budgets for availability during cluster maintenance
- Horizontal Pod Autoscaler for automatic scaling based on load
- Multiple replicas with anti-affinity rules for high availability

**Resource Management:**
- Node selectors or affinity rules for optimal pod placement
- Resource quotas and limit ranges for the Wave namespace

### Advanced Configuration

For advanced Wave features, scaling guidance, and integration options, see [Configuring Wave](../configuring-wave.md).




