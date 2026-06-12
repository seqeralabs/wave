FROM ubuntu:22.04
ARG version
RUN apt-get update && apt-get install -y ca-certificates file curl tar
RUN curl -LO https://github.com/Barre/ZeroFS/releases/download/v${version}/zerofs-pgo-multiplatform.tar.gz \
    && tar -xzf zerofs-pgo-multiplatform.tar.gz \
    && cp zerofs-linux-amd64-pgo /usr/local/bin/zerofs \
    && chmod +x /usr/local/bin/zerofs

