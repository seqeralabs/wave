FROM alpine:3.21.3
ARG SINGULARITY_VERSION
RUN apk update
RUN apk add singularity=${SINGULARITY_VERSION}

ARG TARGETPLATFORM
ARG FUSION_VERSION

RUN case "${TARGETPLATFORM}" in \
        "linux/amd64") ARCH="amd64" ;; \
        "linux/arm64") ARCH="arm64" ;; \
        *) echo "Unsupported platform: ${TARGETPLATFORM}" && exit 1 ;; \
    esac && \
    wget -nv -O - https://fusionfs.seqera.io/releases/pkg/${FUSION_VERSION//./\/}/fusion-${ARCH}.tar.gz | tar zx

ENTRYPOINT ["/usr/bin/fusion"]
