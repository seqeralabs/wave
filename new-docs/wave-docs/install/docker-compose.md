---
title: Docker Compose installation
---

Wave enables you to provision container images on demand, removing the need to build and upload them manually to a container registry. Wave can provision both ephemeral and regular registry-persisted container images.

Docker Compose installations support Wave Lite, a configuration mode for Wave that includes only container augmentation and inspection capabilities, and enables the use of Fusion file system in Nextflow pipelines.

## Prerequisites

Before installing Wave, you need the following infrastructure components:

- **PostgreSQL instance** - Version 12, or higher
- **Redis instance** - Version 6.2, or higher

## System requirements

The minimum system requirements for self-hosted Wave in Docker Compose are:

- Current, supported versions of **Docker Engine** and **Docker Compose**.
- Compute instance minimum requirements:
  - **Memory**: 32 GB RAM available to be used by the Wave application on the host system.
  - **CPU**: 8 CPU cores available on the host system.
  - **Storage**: 10 GB in addition to sufficient disk space for your container images and temporary files.
  - For example, in AWS EC2, `m5a.2xlarge` or greater
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

```yaml
wave:
  # Build service configuration - disabled for Docker Compose
  build:
    enabled: false
  # Mirror service configuration - disabled for Docker Compose
  mirror:
    enabled: false
  # Security scanning configuration - disabled for Docker Compose
  scan:
    enabled: false
  # Blob caching configuration - disabled for Docker Compose
  blobCache:
    enabled: false
  # Database connection settings
  db:
    uri: "jdbc:postgresql://your-postgres-host:5432/wave"
    user: "wave_user"
    password: "your_secure_password"

# Redis configuration for caching and session management
redis:
  uri: "redis://your-redis-host:6379"

# Platform integration (optional)
tower:
  endpoint:
    url: "https://your-platform-server.com"

# Micronaut framework configuration
micronaut:
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

- Replace `your-postgres-host` and `your-redis-host` with your service endpoints.
- Adjust `number-of-threads` (16) and `num-threads` (64) based on your CPU cores — Use between 2x and 4x your CPU core count.

## Docker Compose

Add the following to your `docker-compose.yml`:

```yaml
services:
  wave-app:
    image: your-registry.com/wave:latest
    container_name: wave-app
    ports:
      # Bind to the host on 9100 vs 9090
      - "9100:9090"
    environment:
      - MICRONAUT_ENVIRONMENTS=lite,redis,postgres
    volumes:
      - ./config/wave-config.yml:/work/config.yml:ro
    deploy:
      mode: replicated
      replicas: 2
      resources:
        limits:
          memory: 4G
          cpus: '1.0'
        reservations:
          memory: 4G
          cpus: '1'
    # Health check configuration
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9090/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    # Restart policy
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
