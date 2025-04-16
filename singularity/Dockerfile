FROM alpine:edge

RUN apk update
RUN apk add singularity
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

USER builder
