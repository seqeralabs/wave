---
title: Kubernetes installation
description: Deploy Wave Lite on any Kubernetes cluster with external PostgreSQL and Redis.
---

Install Wave Lite on Kubernetes when you want the Wave Lite configuration on a cluster you already run. This installs container augmentation, inspection, and private registry authentication. Build, mirror, and scan are not part of Wave Lite. The full Wave configuration adds them to a Wave Lite install on Amazon EKS. After installing Wave Lite on an EKS cluster, follow [Enable Wave builds](aws-build.md).

For other choices (different ingress controllers or untested distributions), see [Adapt this guide](#adapt-this-guide).

:::info[**Prerequisites**]

You need the following:

- A Kubernetes cluster, version 1.31 or later, with permission to create namespaces, deployments, and services.
- Cluster capacity for the Wave service's minimum compute requirements:
  - Memory: 12 GB RAM (8 GB for Wave pods, plus headroom for the cluster).
  - CPU: 4 cores (2 for Wave pods, plus headroom for the cluster).
  - Storage: 10 GB, plus disk space for your container images and temporary files.
  - Network: connectivity to your PostgreSQL and Redis instances.
- PostgreSQL 16 or later, reachable from the cluster.
- Redis 6.2 or later, reachable from the cluster.
- A Seqera Platform deployment and its endpoint URL.
- Access to the Wave container image from `cr.seqera.io`, using credentials provided by Seqera.

:::

## Create the database

Create a dedicated `wave` database and a `wave_user` role on your managed PostgreSQL instance:

```sql
-- Create a dedicated user for Wave
CREATE ROLE wave_user LOGIN PASSWORD '<db-password>';

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

Wave applies its schema migrations on startup.

## Create the namespace

Create a dedicated `wave` namespace to hold the Wave service and its resources:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: "wave"
  labels:
    app: wave-app
```

## Configure Wave

Create a ConfigMap with Wave's configuration. Update the database, Redis, and Platform values to match your environment.

:::warning
This ConfigMap contains sensitive values. Use a Kubernetes Secret for credentials and reference it from the deployment rather than embedding secrets in the ConfigMap. See the [Kubernetes Secrets documentation](https://kubernetes.io/docs/concepts/configuration/secret/).
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
      # Wave Lite: build, mirror, scan, and blob cache disabled.
      build:
        enabled: false
      mirror:
        enabled: false
      scan:
        enabled: false
      blobCache:
        enabled: false
      server:
        url: "https://wave.example.com"
      db:
        uri: "jdbc:postgresql://postgres.example.com:5432/wave"
        user: "wave_user"
        password: "<db-password>"
    redis:
      uri: "rediss://redis.example.com:6379"
    tower:
      endpoint:
        url: "https://platform.example.com/api"
```

:::warning
Set `wave.server.url` to the address clients use to reach Wave. If it is left unset, Wave issues container tokens pointing at `http://localhost:9090`, which clients cannot reach.
:::

This ConfigMap sets only what Wave Lite needs to start. To configure other options, such as rate limits, token cache duration, and metrics, see [Configure Wave](configure-wave.md). Before serving production traffic, complete the [production hardening](configure-wave.md#harden-for-production) checklist.

## Authenticate to private registries

Wave Lite pulls images during augmentation. To augment images from a private registry, give Wave credentials for that registry. Wave resolves credentials in this order:

1. **Platform workspace credentials**: credentials a user adds to their Seqera Platform workspace. Wave uses these for targets the user owns, such as the user's own registry namespace.
2. **Server-side static credentials**: credentials the operator sets under `wave.registries.<host>` for registries Wave owns or shares.

Add an entry for each private registry under `wave.registries` in the `wave-cfg` config. For example, Docker Hub and a private Quay.io account:

```yaml
wave:
  registries:
    docker.io:
      username: "<docker-user>"
      password: "<docker-pat>"
    quay.io:
      username: "<quay-user>"
      password: "<quay-pat>"
```

As with the database and Redis credentials above, keep these out of the ConfigMap in production. Store them in a Kubernetes Secret and reference it from the deployment.

Configure credentials for every private registry Wave pulls from. Public images need none. For all registry options, see [Reference](reference.md#container-registry).

## Create the deployment

Deploy Wave with a Deployment that pulls the Wave image and mounts the `wave-cfg` ConfigMap:

```yaml
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
        - image: cr.seqera.io/<wave-image-path>:<tag>
          name: wave-app
          ports:
            - containerPort: 9090
          env:
            - name: MICRONAUT_ENVIRONMENTS
              value: "lite,postgres,redis"   # The `k8s` env is only needed for the in-cluster build client (see aws-build).
          resources:
            requests:
              memory: "2Gi"
              cpu: "0.2"
            limits:
              memory: "4Gi"
              cpu: "1"
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

## Create the service

Expose the Wave pods inside the cluster with a Service that the ingress routes to:

```yaml
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

## Expose Wave

Wave must be reachable from Seqera Platform and from your Nextflow compute environments. Front the service with an ingress and terminate TLS at the ingress or load balancer. Wave does not terminate TLS itself.

This example uses an AWS ALB. For NGINX or GCE ingress, see [Adapt this guide](#adapt-this-guide).

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: wave-ingress
  namespace: wave
spec:
  rules:
    - host: wave.example.com
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

:::note
This minimal Ingress omits controller-specific configuration. For the AWS Load Balancer Controller, add `ingressClassName: alb` and the `alb.ingress.kubernetes.io/*` annotations (scheme, target type, and ACM certificate ARN) your setup requires.
:::

After the ingress provisions, configure your Seqera Platform deployment to use the Wave endpoint by setting the Wave server URL in `tower.yml` ([Platform Wave configuration](https://docs.seqera.io/platform-enterprise/latest/enterprise/configuration/wave)).

:::note
For production reliability, add Pod Disruption Budgets, a Horizontal Pod Autoscaler, multiple replicas with anti-affinity, and resource quotas for the `wave` namespace. See [production hardening](configure-wave.md#harden-for-production).
:::

## Verify your installation

Confirm the service is live and functional. See [Verify your installation](post-install.md) for the `/service-info` check and the `wave-cli` functional test.

When Wave is running and verified, continue to [Configure Wave](configure-wave.md#harden-for-production) to harden the deployment for production.

## Adapt this guide

The supported procedure uses managed PostgreSQL and Redis and an AWS ALB ingress. The options below are described, not wired into the steps above. Adapt them at your own risk.

- **Other ingress controllers**: NGINX, GCE, or Traefik work, but add the provider-specific annotations they require and verify TLS termination.
- **Other distributions**: Wave Lite has no AWS dependency and should run on any conformant Kubernetes distribution. These may work but are not validated.
