FROM --platform=$BUILDPLATFORM alpine:3.22.0
ARG SINGULARITY_VERSION

ARG TARGETPLATFORM
ARG FUSION_VERSION

RUN case "${TARGETPLATFORM}" in \
        "linux/amd64") ARCH="amd64" ;; \
        "linux/arm64") ARCH="arm64" ;; \
        *) echo "Unsupported platform: ${TARGETPLATFORM}" && exit 1 ;; \
    esac && \
    wget -nv -O - https://fusionfs.seqera.io/releases/pkg/${FUSION_VERSION//./\/}/fusion-${ARCH}.tar.gz | tar zx

RUN apk update
RUN apk add singularity=${SINGULARITY_VERSION}
RUN apk add proot-static --repository=http://dl-cdn.alpinelinux.org/alpine/edge/testing/

RUN ln -s /usr/bin/proot.static /usr/bin/proot

RUN addgroup -g 1000 builder

RUN adduser \
    -D \
    -u 1000 \
    -G builder \
    -h /home/builder \
    -s /bin/sh \
    builder

WORKDIR /home/builder
USER builder
