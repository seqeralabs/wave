---
title: Install Wave Lite with Docker Compose
description: Deploy Wave Lite on a single Docker host with external PostgreSQL and Redis.
---

Install Wave Lite with Docker Compose when you want the Lite configuration without Kubernetes, for example a compliance-constrained site that cannot run EKS. This installs container augmentation, inspection, and private registry authentication. A Docker Compose deployment cannot be extended to the full Wave configuration, which requires Kubernetes on Amazon EKS.

For other choices (embedded databases, or exposing Wave behind HTTPS), see [Adapt this guide](#adapt-this-guide).

:::info[**Prerequisites**]

You need the following:

- Current, supported versions of Docker Engine and Docker Compose.
- A host that meets the Wave service's minimum compute requirements:
  - Memory: 12 GB RAM (8 GB for Wave replicas, plus headroom for the OS and Docker).
  - CPU: 4 cores (2 for Wave replicas, plus headroom for the OS and Docker).
  - Storage: 10 GB, plus disk space for your container images and temporary files.
  - Network: Connectivity to your PostgreSQL and Redis instances.
  - On AWS EC2, an `m5a.2xlarge` instance.
- PostgreSQL 16 or later.
- Redis 6.2 or later.
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

Create `config.yml` alongside `wave.env`. Wave Lite runs with build, mirror, scan, and blob cache disabled:

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
```

This file sets only what Wave Lite needs to start. The `lite` entry in `MICRONAUT_ENVIRONMENTS`, set in the Compose file in a later step, already applies these same defaults. The file restates them explicitly and gives you a place to add further configuration. To configure other options, such as rate limits, token cache duration, and metrics, see [Configure Wave](configure-wave.md). Before serving production traffic, complete the [production checklist](configure-wave.md#production-checklist).

## Authenticate to private registries

Wave Lite pulls images during augmentation. To augment images from a private registry, give Wave credentials for that registry. Wave uses one of two credential sources per request:

- **Platform workspace credentials**: credentials a user adds to their Seqera Platform workspace. Wave uses these for requests that carry a Platform identity.
- **Server-side static credentials**: credentials the operator sets. Wave uses these for anonymous requests and for registries the operator owns.

For the common registries, set the credentials as environment variables in `wave.env`:

```bash
# Docker Hub
DOCKER_USER=<docker-user>
DOCKER_PAT=<docker-pat>
```

```bash
# Quay.io
QUAY_USER=<quay-user>
QUAY_PAT=<quay-pat>
```

For any other registry, add an entry under `wave.registries.<host>` in `config.yml`:

```yaml
wave:
  registries:
    myregistry.example.com:
      username: "<username>"
      password: "<password>"
```

Configure credentials for every private registry Wave pulls from. Public images need none. For all registry options, see [Container registry](reference.md#container-registry).

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
      - MICRONAUT_ENVIRONMENTS=lite,rate-limit,redis,postgres,prometheus
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

Docker Compose runs Wave in one of two modes, depending on whether you need more than one replica:

- **Single host**: Run `docker compose up -d`. This starts one Wave replica on the local Docker host.
- **Swarm (two or more replicas)**: Set `replicas: 2` in `docker-compose.yml`, then run `docker stack deploy -c docker-compose.yml wave`.

On first startup, Wave takes 30 to 60 seconds to initialize while it applies database migrations.

:::warning
If Wave Lite runs in the same Swarm as Platform Connect for [Studios](https://docs.seqera.io/platform-enterprise/25.2/enterprise/studios#docker-compose), removing the stack also interrupts Connect services.
:::

## Verify your installation

Confirm the service is live and functional. See [Verify your installation](post-install.md) for the `/service-info` check and the Wave CLI functional checks.

When Wave is running and verified, continue to the [production checklist](configure-wave.md#production-checklist) to prepare the deployment for production.

## Adapt this guide

The supported procedure uses managed PostgreSQL and Redis and assumes you front Wave yourself. The following options are described but not part of the procedure. Adapt them at your own risk.

- **Embedded PostgreSQL and Redis**: Suitable for development and testing. Not supported for production.
- **Expose Wave externally over HTTPS**: Front the service with a load balancer and certificate (for example, AWS ALB with ACM and Route 53).
