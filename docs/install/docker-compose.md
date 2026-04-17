---
title: Docker Compose installation
---

Wave enables you to provision container images on demand, removing the need to build and upload them manually to a container registry. Wave can provision both ephemeral and regular registry-persisted container images.

Docker Compose installations support Wave Lite, a configuration mode for Wave that includes only container augmentation and inspection capabilities, and enables the use of Fusion file system in Nextflow pipelines.

## Prerequisites

Before installing Wave, you need the following infrastructure components:

- **PostgreSQL instance** - Version 12, or higher
- **Redis instance** - Version 6.2, or higher

:::note
Use managed services for PostgreSQL and Redis (e.g., Amazon RDS, Amazon ElastiCache, or equivalent) rather than running them in Docker Compose. Managed services provide automated backups, failover, patching, and monitoring that are difficult to replicate with containerized databases. Running PostgreSQL or Redis in Docker Compose is suitable only for local development and testing.
:::

## System requirements

The minimum system requirements for self-hosted Wave in Docker Compose are:

- Current, supported versions of **Docker Engine** and **Docker Compose**.
- Compute instance minimum requirements:
  - **Memory**: 16 GB RAM available to be used by the Wave application on the host system.
  - **CPU**: 4 CPU cores available on the host system.
  - **Storage**: 10 GB in addition to sufficient disk space for your container images and temporary files.
  - For example, in AWS EC2, `m5a.xlarge` or greater
  - **Network**: Connectivity to your PostgreSQL and Redis instances.

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

Wave will automatically handle schema migrations on startup and create the required database objects.

## Wave config

Create a configuration file that defines Wave's behavior and integrations. Save this as `config/wave-config.yml` in your Docker Compose directory.

Database, Redis, and Platform connection settings are provided via the `wave.env` environment file (see [Deploy Wave](#deploy-wave) below), so they do not need to be duplicated here.

```yaml
wave:
  debug: false
  tokens:
    cache:
      duration: "36h"
  metrics:
    enabled: true

# Rate limiting configuration
rate-limit:
  pull:
    anonymous: 250/1h
    authenticated: 2000/1m
  timeout-errors:
    max-rate: 100/1m

# Micronaut framework configuration
micronaut:
  # Netty HTTP server configuration
  netty:
    event-loops:
      default:
        num-threads: 64
  # HTTP client configuration
  http:
    services:
      stream-client:
        read-timeout: '30s'
        read-idle-timeout: '5m'

# Management endpoints configuration
endpoints:
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

Configuration notes:

- Adjust `num-threads` (64) based on your CPU cores — use between 2x and 4x your CPU core count.

## Docker Compose

Add the following to your `docker-compose.yml`:

```yaml
services:
  wave:
    # Replace with your Wave image registry path
    image: cr.seqera.io/public/wave:latest
    ports:
      - "9090:9090"
    environment:
      - MICRONAUT_ENVIRONMENTS=lite,rate-limit,redis,postgres,prometheus
    volumes:
      - ./config/wave-config.yml:/work/config.yml:ro
    env_file:
      - wave.env
    working_dir: /work
    deploy:
      mode: replicated
      replicas: 2
      resources:
        limits:
          memory: 4G
          cpus: '1.0'
        reservations:
          memory: 2G
          cpus: '0.5'
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9090/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    restart: unless-stopped
```

## Deploy Wave

1. Download and populate the [wave.env](./_templates/wave.env) file with the settings corresponding to your system.

1. Use Docker Swarm to deploy Wave Lite. See [Create a swarm](https://docs.docker.com/engine/swarm/swarm-tutorial/create-swarm/) for detailed setup instructions.


1. Deploy the Wave service, running two replicas:

    ```bash
    docker stack deploy -c docker-compose.yml mystack
    ```

    :::note
    Wave is available at `http://localhost:9090` once the container is running and healthy. The application may take 30-60 seconds to fully initialize on first startup, as it performs database migrations.
    :::

1. Check the current status:

    ```bash
    docker service ls
    ```

1. Check the logs:

    ```bash
    docker service logs mystack_wave
    ```

1. Tear down the service when it's no longer needed:

    ```bash
    docker stack rm mystack
    ```

    :::warning
    If Wave Lite is running in the same container as Platform Connect for [Studios](https://docs.seqera.io/platform-enterprise/25.2/enterprise/studios#docker-compose), tearing down the service will also interrupt Connect services.
    :::

### Advanced configuration

See [Configure Wave](../configure-wave.md) for advanced Wave features, scaling guidance, and integration options.
