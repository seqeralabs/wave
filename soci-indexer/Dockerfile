# soci-indexer: builds SOCI zTOCs for Wave. The soci ztoc library uses cgo and links zlib statically,
# so the binary is cross-compiled with the target's C toolchain on the build platform: as condasplit,
# no step runs under emulation
FROM --platform=$BUILDPLATFORM golang:1.26-bookworm AS build
ARG TARGETARCH
ARG BUILDARCH
RUN case "$TARGETARCH" in amd64) triplet=x86_64-linux-gnu ;; arm64) triplet=aarch64-linux-gnu ;; *) exit 1 ;; esac \
 && pkgs="zlib1g-dev:$TARGETARCH" \
 && if [ "$TARGETARCH" != "$BUILDARCH" ]; then pkgs="$pkgs gcc-$(echo $triplet | tr _ -)"; fi \
 && dpkg --add-architecture $TARGETARCH && apt-get update -qq && apt-get install -y -qq $pkgs >/dev/null \
 && echo "$triplet-gcc" > /cc
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY *.go ./
RUN CC=$(cat /cc) CGO_ENABLED=1 GOOS=linux GOARCH=$TARGETARCH \
    go build -trimpath -ldflags '-s -w -extldflags "-static"' -o /soci-indexer .

# CA certificates and /tmp (the layer download), nothing else: the jobs run the binary directly
FROM gcr.io/distroless/static-debian12
COPY --from=build /soci-indexer /usr/local/bin/soci-indexer
ENTRYPOINT []
