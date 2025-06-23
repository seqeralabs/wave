FROM --platform=$BUILDPLATFORM moby/buildkit:v0.22.0-rootless
USER root

ARG TARGETPLATFORM
ARG BUILDKIT_VERSION
ARG FUSION_VERSION

ENV FUSION_VERSION=2.4.13

RUN case "${TARGETPLATFORM}" in \
        "linux/amd64") ARCH="amd64" ;; \
        "linux/arm64") ARCH="arm64" ;; \
        *) echo "Unsupported platform: ${TARGETPLATFORM}" && exit 1 ;; \
    esac && \
    wget -nv -O - https://fusionfs.seqera.io/releases/pkg/${FUSION_VERSION//./\/}/fusion-${ARCH}.tar.gz | tar zx
