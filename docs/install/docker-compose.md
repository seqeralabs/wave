---
title: Install Wave Lite with Docker Compose
description: Deploy Wave Lite on a single Docker host with external PostgreSQL and Redis.
---

Install Wave Lite with Docker Compose when you want the Lite configuration without Kubernetes, for example a compliance-constrained site that cannot run EKS. This installs container augmentation, inspection, and private registry authentication. A Docker Compose deployment cannot be extended to the full Wave configuration, which requires Kubernetes on Amazon EKS.

:::info[**Prerequisites**]

You need the following:

- Current, supported versions of Docker Engine and Docker Compose.
- A host with capacity for each Wave replica you run. One replica reserves 2 GB RAM and 0.2 CPU and is limited to 4 GB and 1 CPU, so budget 6 GB and 2 cores per replica including headroom for the OS and Docker. On AWS EC2, an `m5a.xlarge` runs one replica comfortably.
- 10 GB storage, plus disk space for container images and temporary files.
- PostgreSQL 16 or later, reachable from the host.
- Redis 6.2 or later, reachable from the host.
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

## Set the connection environment

Create a `wave.env` file with the values Wave needs to reach its database, Redis, and Seqera Platform:

```bash
# Base URL clients use to reach the Wave service.
WAVE_SERVER_URL=https://wave.example.com

# PostgreSQL connection.
WAVE_DB_URI=jdbc:postgresql://postgres.example.com:5432/wave
WAVE_DB_USER=wave_user
WAVE_DB_PASSWORD=<db-password>

# Redis connection. Use rediss:// for TLS (typical for managed Redis),
# or redis:// for a plain connection.
REDIS_URI=rediss://redis.example.com:6379

# Seqera Platform endpoint to pair with.
TOWER_ENDPOINT_URL=https://platform.example.com/api
```

:::warning
Set `WAVE_SERVER_URL` to the address your clients use to reach Wave. If you leave it unset, Wave issues container tokens pointing at `http://localhost:9090`, which clients cannot reach.
:::

## Configure Wave

Wave requires a `config.yml` in its working directory and fails to start without one. Create it alongside `wave.env`:

```yaml
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

# Keep the JDBC and disk-space indicators out of /health. Micronaut enables
# them by default, so a brief database blip turns the healthcheck below red
# and, under Swarm, gets the task replaced.
endpoints:
  health:
    enabled: true
    disk-space:
      enabled: false
    jdbc:
      enabled: false
```

The `lite` entry in `MICRONAUT_ENVIRONMENTS`, set in the Compose file in a later step, already applies the four feature toggles. The file restates them explicitly and gives you a place to add further configuration. For every available setting, see the [Configuration reference](reference.md). Before serving production traffic, complete the [production checklist](configure-wave.md#production-checklist).

## Authenticate to private registries

Wave Lite pulls images during augmentation. To augment images from a private registry, give Wave credentials for that registry. Wave uses one of two credential sources per request:

- **Platform workspace credentials**: credentials a user adds to their Seqera Platform workspace. Wave uses these for requests that carry a Platform identity.
- **Server-side static credentials**: credentials the operator sets under `wave.registries.<host>`. Wave uses these for anonymous requests and for registries the operator owns.

Add an entry per registry to `config.yml`. Wave reads static credentials only from `wave.registries`, so keep the values out of `wave.env` unless you interpolate them here:

```yaml
wave:
  registries:
    docker.io:
      username: "${DOCKER_USER:}"
      password: "${DOCKER_PAT:}"
    quay.io:
      username: "${QUAY_USER:}"
      password: "${QUAY_PAT:}"
    myregistry.example.com:
      username: "<username>"
      password: "<password>"
```

The `${VAR:}` form reads the value from the environment, so with the block above you can put `DOCKER_USER` and `DOCKER_PAT` in `wave.env` and keep the secrets out of `config.yml`. Configure credentials for every private registry Wave pulls from. Public images need none. For all registry options, see [Container registry](reference.md#container-registry).

:::warning
Anonymous access is enabled by default, so any client that can reach Wave can use these operator credentials to pull through it. Disable it with `wave.capabilities.anonymous-access: false` before you expose the service — see [Require authentication](configure-wave.md#require-authentication).
:::

## Log in to the Seqera container registry

The Wave image is hosted on `cr.seqera.io`, which requires authentication. Log in with the credentials provided by Seqera before starting the service:

```bash
docker login cr.seqera.io -u <username>
```

## Create the Compose file

Define the Wave service in `docker-compose.yml`. Replace `<wave-image-path>` and `<tag>` with the image path and tag provided by Seqera:

```yaml
services:
  wave:
    image: cr.seqera.io/<wave-image-path>:<tag>
    ports:
      - "9090:9090"
    volumes:
      - ./config.yml:/work/config.yml:ro
    environment:
      # prometheus exposes metrics for scraping. Remove it if you do not collect metrics.
      - MICRONAUT_ENVIRONMENTS=lite,postgres,redis,rate-limit,prometheus
    env_file:
      - wave.env
    working_dir: /work
    deploy:
      mode: replicated
      replicas: 1
      resources:
        limits:
          memory: 4G
          cpus: '1.0'
        reservations:
          memory: 2G
          cpus: '0.2'
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9090/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    restart: unless-stopped
```

## Start Wave

Start the service:

```bash
docker compose up -d
```

On first startup, Wave takes 30 to 60 seconds to initialize while it applies database migrations.

For two or more replicas, raise `replicas` and deploy the same file as a Swarm stack instead — see [Deploy a stack to a swarm](https://docs.docker.com/engine/swarm/stack-deploy/).

:::warning
If Wave Lite runs in the same Swarm as Platform Connect for [Studios](https://docs.seqera.io/platform-enterprise/25.2/enterprise/studios#docker-compose), removing the stack also interrupts Connect services.
:::

## Verify your installation

Confirm the service is live and functional. See [Verify your installation](post-install.md) for the `/service-info` check and the Wave CLI functional checks.

When Wave is running and verified, continue to the [production checklist](configure-wave.md#production-checklist) to prepare the deployment for production. That is also where TLS termination is covered: this procedure assumes managed PostgreSQL and Redis, and that you front Wave with your own load balancer.
