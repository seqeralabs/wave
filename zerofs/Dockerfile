# Stage 1: Setup
FROM alpine:3.22.1 AS setup

RUN apk add --no-cache curl tar

RUN curl -LO https://github.com/Barre/ZeroFS/releases/latest/download/zerofs-pgo-multiplatform.tar.gz && \
    tar -xzf zerofs-pgo-multiplatform.tar.gz && \
    ls -la zerofs-linux-amd64-pgo && \
    cp zerofs-linux-amd64-pgo /zerofs && \
    chmod +x /zerofs && \
    ls -la /zerofs

# Stage 2: Runtime
FROM ubuntu:22.04

RUN apt-get update && apt-get install -y ca-certificates

COPY --from=setup /zerofs /shared/zerofs

CMD ["/bin/sh", "-c", "echo 'Binary info:' && file /shared/zerofs || echo 'file command not available' && ldd /shared/zerofs || echo 'ldd not available' && echo 'Starting ZeroFS...' && exec /shared/zerofs run -c /config/zerofs.toml"]
