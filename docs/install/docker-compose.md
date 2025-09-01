---
title: Install Wave Lite with Docker Compose
description: Install Wave Lite with Docker Compose
tags: [docker compose, install, wave]
---

Docker Compose provides a straightforward deployment method for deploying Wave. It packages Wave services and dependencies into a coordinated container stack. This approach handles service orchestration automatically, coordinating startup and networking of Wave components.

Docker Compose installations support [Wave Lite](../wave-lite.md), a configuration mode for Wave that includes container augmentation and inspection capabilities, and enables the use of Fusion file system in Nextflow pipelines.

This page describes how to run Wave Lite with Docker Compose. It includes steps to:

- Configure a database
- Configure Wave
- Set up Docker Compose
- Deploy Wave
- Configure advanced options

:::info[**Prerequisites**]
You will need the following to get started:

- A PostgreSQL instance (version 12 or higher)
- A Redis instance (version 6.2 or higher)
- Supported versions of Docker Engine and Docker Compose
- A compute instance with at least:
    - **Memory**: 32 GB RAM available for use by the Wave application on the host system
    - **CPU**: 8 CPU cores available on the host system
    - **Storage**: 10 GB in addition to sufficient disk space for your container images and temporary files (for example, in AWS EC2, `m5a.2xlarge` or greater)
    - **Network**: Connectivity to your PostgreSQL and Redis instances
:::

## Configure a database

To configure a PostgreSQL database, follow these steps:

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

:::note
Wave will automatically handle schema migrations and create the required database objects on startup.
:::

## Configure Wave

To configure Wave's behavior and integrations:

- Create `config/wave-config.yml` with the following configuration:

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
        uri: "jdbc:postgresql://<POSTGRES_HOST>:5432/wave"
        user: "wave_user"
        password: "<SECURE_PASSWORD>"

    # Redis configuration for caching and session management
    redis:
      uri: "redis://<REDIS_HOST>:6379"

    # Platform integration (optional)
    tower:
      endpoint:
        url: "<PLATFORM_SERVER>"

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

    Replace the following:

    - `<POSTGRES_HOST>`: your Postgres service endpoint
    - `<SECURE_PASSWORD>`: your secure password for the database user
    - `<REDIS_HOST>`: your Redis service endpoint
    - `<PLATFORM_SERVER>`: your Platform endpoint URL (_optional_)

    Adjust the following values based on your CPU cores:

    - `number-of-threads`: Set between 2x and 4x your CPU core count (default: 16)
    - `num-threads`: Set between 2x and 4x your CPU core count (default: 64)

## Configure Docker Compose

To configure Waves Docker Compose deploy:

- Create `docker-compose.yml` with the following configuration:

    ```yaml
    services:
      wave-app:
        image: <REGISTRY>/wave:latest
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

    Replace `<REGISTRY>` with your registry image.

## Deploy Wave

To deploy Wave using Docker Swarm, follow these steps:

1. Download and populate the [wave.env](./_templates/wave.env) file with the settings corresponding to your system.

1. Use Docker Swarm to deploy Wave. For detailed setup instructions, see [Create a swarm](https://docs.docker.com/engine/swarm/swarm-tutorial/create-swarm/).

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
    If Wave is running in the same container as Platform Connect for [Studios](https://docs.seqera.io/platform-enterprise/25.2/enterprise/studios#docker-compose), tearing down the service will also interrupt Connect services.
    :::

## Configure advanced options

For advanced Wave features, scaling guidance, and integration options, see [Configuring Wave](./configure-wave.md).
