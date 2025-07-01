---
title: Docker Compose installation
---

Docker Compose installations support Wave in Lite mode. Wave Lite includes only container augmentation and inspection capabilities, and enables the use of Fusion file system in Nextflow pipelines. The following features are not available in self-hosted Wave installations in Docker Compose:

- Container Freeze
- Container Build service
- Container Mirror service
- Container Security scanning
- Container blobs caching 

For full Wave functionality, an AWS Elastic Kubernetes instance is required.

## Prerequisites

Before installing Wave, you require the following infrastructure components:

- **PostgreSQL instance** - Version 12, or higher 
- **Redis instance** - Version 6.2, or higher

## System requirements

The minimum system requirements for self-hosted Wave in Docker Compose are:

- Current, supported versions of **Docker Engine** and **Docker Compose**.
- **EC2 instance**: `m5a.2xlarge` or greater:
  - **Memory**: 32 GB RAM available to be used by the Wave application on the host system. 
  - **CPU**: 8 CPU cores available on the host system. 
  - **Storage**: 10 GB minimum, in addition to sufficient disk space for your container images and temporary files.
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
      - "9090:9090"
    environment:
      MICRONAUT_ENVIRONMENTS: "postgres,redis,lite"
    working_dir: /work
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

2. Initialize the Docker swarm environment:
  
    docker swarm init

3. Deploy the Wave service, running 2 replicas:
  
    docker stack deploy -c docker-compose.yml mystack

4. Check the current status:
  
    docker service ls

5. Check the logs:

    docker service logs mystack_wave

6. Tear down the service when it's no longer needed:

    docker stack rm mystack

Wave will be available at `http://localhost:9090` once the container is running and healthy. The application may take 30-60 seconds to fully initialize on first startup, as it performs database migrations.

### Advanced configuration

See [Configuring Wave](./configuring-wave.md) for advanced Wave features, scaling guidance, and integration options.
