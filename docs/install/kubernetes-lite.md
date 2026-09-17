---
title: Install Wave Lite on Kubernetes
description: Deploy Wave Lite on any Kubernetes cluster with external PostgreSQL and Redis.
---

Install Wave Lite on a Kubernetes cluster you already operate. This installs container augmentation, inspection, and private registry authentication. Build, mirror, and scan are not part of Wave Lite. The full Wave configuration adds them to a Wave Lite install on Amazon EKS. After you install Wave Lite on an EKS cluster, follow [Enable Wave builds](aws-build.md).

:::info[**Prerequisites**]

You need the following:

- A Kubernetes cluster, version 1.31 or later, with permission to create namespaces, deployments, and services.
- Cluster capacity for each Wave pod. The deployment in this guide requests 2 GB RAM and 0.2 CPU per pod and limits it to 4 GB and 1 CPU. Scale that by your replica count.
- 10 GB storage, plus disk space for container images and temporary files.
- PostgreSQL 16 or later, reachable from the cluster.
- Redis 6.2 or later, reachable from the cluster.
- A Seqera Platform deployment and its endpoint URL.
- Access to the Wave container image from `cr.seqera.io`, using credentials provided by Seqera.
:::

:::tip[Install with the Helm chart]
Seqera publishes an official [Wave Helm chart](https://artifacthub.io/packages/helm/seqera/wave) that deploys Wave Lite as an alternative to the raw manifests in this guide. Follow the chart's documentation to install it, and note the following:

- Create the database first, as described in [Create the database](#create-the-database), and verify the deployment with the same [post-install checks](post-install.md).
- Add `rate-limit` to the chart's `micronautEnvironments` value to activate the rate limits described in [Set rate limits](configure-wave.md#set-rate-limits).
- Confirm the `WAVE_SERVER_URL` environment variable on the running pod resolves to the hostname clients use to reach Wave. Override it with the chart's `extraEnvVars` value if it does not.
- The chart deploys Wave Lite only. To run the full Wave configuration, deploy on Amazon EKS and follow [Enable Wave builds](aws-build.md) on top of the chart release.
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

## Create the registry credentials secret

The Wave image is hosted on `cr.seqera.io`, which requires authentication. Create a pull secret in the `wave` namespace with the credentials provided by Seqera. The deployment in a later step references it as `seqera-reg-creds`:

```bash
kubectl create secret docker-registry seqera-reg-creds \
  --namespace wave \
  --docker-server=cr.seqera.io \
  --docker-username=<username> \
  --docker-password=<password>
```

## Configure Wave

Create a ConfigMap that holds the Wave configuration. The ConfigMap is the entire `config.yml` because Wave loads a single YAML document. Add settings inside this block rather than appending a second `wave:` section. Update the database, Redis, Platform, and registry values to match your environment.

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
      # One entry per private registry Wave pulls from. Public images need none.
      registries:
        docker.io:
          username: "<docker-user>"
          password: "<docker-pat>"
        quay.io:
          username: "<quay-user>"
          password: "<quay-pat>"
    redis:
      # Use rediss:// for TLS (typical for managed Redis), or redis:// for a plain connection.
      uri: "rediss://redis.example.com:6379"
    tower:
      endpoint:
        url: "https://platform.example.com/api"
    # Keep the JDBC and disk-space indicators out of /health. Micronaut enables them
    # by default, and the liveness probe below would restart every pod on a brief
    # database blip.
    endpoints:
      health:
        enabled: true
        disk-space:
          enabled: false
        jdbc:
          enabled: false
```

:::warning
Set `wave.server.url` to the address clients use to reach Wave. If you leave it unset, Wave issues container tokens pointing at `http://localhost:9090`, which clients cannot reach.
:::

The `lite` entry in `MICRONAUT_ENVIRONMENTS`, set in the deployment in a later step, already applies the four feature toggles. The ConfigMap restates them explicitly and gives you a place to add further configuration. For every available setting, see the [Configuration reference](reference.md). Before serving production traffic, complete the [production checklist](configure-wave.md#production-checklist).

## Registry credentials

Wave Lite pulls images during augmentation and uses one of two credential sources per request:

- **Platform workspace credentials**: credentials a user adds to their Seqera Platform workspace. Wave uses these for requests that carry a Platform identity.
- **Server-side static credentials**: the `wave.registries.<host>` entries in the `wave-cfg` ConfigMap. Wave uses these for anonymous requests and for registries the operator owns.

For all registry options, see [Container registry](reference.md#container-registry).

:::warning
Anonymous access is enabled by default. Any client that can reach Wave can use the operator credentials to pull through it. Disable it with `wave.capabilities.anonymous-access: false` before you expose the service. See [Require authentication](configure-wave.md#require-authentication).
:::

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
      imagePullSecrets:
        - name: seqera-reg-creds
      containers:
        - image: cr.seqera.io/<wave-image-path>:<tag>   # Use the image path and tag provided by Seqera.
          name: wave-app
          ports:
            - containerPort: 9090
          env:
            - name: MICRONAUT_ENVIRONMENTS
              # rate-limit activates the rate-limit.* settings. Add prometheus to expose metrics.
              # The k8s env is only needed for the in-cluster build client (see aws-build).
              value: "lite,postgres,redis,rate-limit"
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

This example uses the AWS Load Balancer Controller. `target-type: ip` lets it route to the `ClusterIP` service defined earlier. With the default `instance` target type, change that service to `NodePort`. Replace the certificate ARN with your own:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: wave-ingress
  namespace: wave
  annotations:
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
    alb.ingress.kubernetes.io/certificate-arn: "arn:aws:acm:<aws-region>:<aws-account-id>:certificate/<certificate-id>"
    alb.ingress.kubernetes.io/healthcheck-path: /health
spec:
  ingressClassName: alb
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

For the certificate and the DNS record that points `wave.example.com` at the load balancer, see [Terminate TLS](configure-wave.md#terminate-tls). For NGINX, GCE, or Traefik, swap `ingressClassName` and the annotations for that controller's equivalents.

## Apply the manifests

Apply the assembled file and wait for the rollout:

```bash
kubectl apply -f wave.yaml
kubectl rollout status deployment/wave -n wave
```

Then configure your Seqera Platform deployment to use the Wave endpoint by setting the Wave server URL in `tower.yml`. See [Platform Wave configuration](https://docs.seqera.io/platform-enterprise/latest/enterprise/configuration/wave).

## Verify your installation

Confirm the service is live and functional. See [Verify your installation](post-install.md) for the `/service-info` check and the Wave CLI functional checks.

When Wave is running and verified, continue to the [production checklist](configure-wave.md#production-checklist) to prepare the deployment for production. Wave Lite has no AWS dependency and runs on any conformant Kubernetes distribution, though only EKS is validated. Only an EKS deployment can be extended to the full Wave configuration.
