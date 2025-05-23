---
title: Compose Installation
---

:::warning

Docker compose installations does not suppot all wave specific features however are suitable as such only 

- inspect
- augment 

will work 

the following features will not work.

- Container Freeze
- Container Build service
- Container Mirror service
- Container Security scanning
- Container blobs caching
:::

For full Wave functionality, consider using Kubernetes.


## Prequesisites

Before installing Wave, ensure you have the following infrastructure components available:

- **PostgreSQL instance** - Version 12 or higher 
- **Redis instance** - Version 6.2+ 

## System Requirements:

The following system requirements are the minimum recommended to get started:

- An to date supported version of **Docker Engine** and **Docker Compose** 
- **Memory**: Minimum 4GB RAM available on the host system and available to be used by the Wave Application. 
- **CPU**: Minimum 1 CPU core available on the host system. 
- **Network**: connectivity to your PostgreSQL and Redis instances
- **Storage**: 10Gb Sufficient disk space for container images and temporary files

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

Wave will automatically handle schema migrations on startup and create the required database objects.

## Wave Config 

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
    password: "your_secure_password_here"

# Redis configuration for caching and session management
redis:
  uri: "redis://your-redis-host:6379"

# Tower/Platform integration (optional)
tower:
  endpoint:
    url: "https://your-tower-server.com"

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

Configuration Notes:

- Replace your-postgres-host and your-redis-host with your actual service endpoints
- Adjust number-of-threads (16) and num-threads (64) based on your CPU cores: use 2-4x your CPU core count

## Docker Compose

Add the following to your docker compose. 

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

## Starting Wave 

Once your configuration files are in place, start Wave using Docker Compose:

**Start Wave in detached mode**

```shell
docker-compose up -d
```

**Check the status of the Wave container**

```shell
docker-compose ps
```

**View Wave logs**

```
docker-compose logs -f wave-app
```

Wave will be available at `http://localhost:9090` once the container is running and healthy. The application may take 30-60 seconds to fully initialize on first startup as it performs database migrations.

### Advanced Configuration

For advanced Wave features, scaling guidance, and integration options, see [Configuring Wave](../configuring-wave.md).
