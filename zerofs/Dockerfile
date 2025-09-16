FROM ubuntu:22.04

RUN apt-get update && apt-get install -y ca-certificates file curl tar
RUN curl -LO https://github.com/Barre/ZeroFS/releases/latest/download/zerofs-pgo-multiplatform.tar.gz \
    && tar -xzf zerofs-pgo-multiplatform.tar.gz \
    && cp zerofs-linux-amd64-pgo /usr/local/bin/zerofs \
    && chmod +x /usr/local/bin/zerofs

