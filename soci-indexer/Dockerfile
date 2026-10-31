# soci-indexer: builds SOCI zTOCs for Wave. The soci ztoc library uses cgo and links zlib statically
FROM golang:1.26-bookworm AS build
RUN apt-get update -qq && apt-get install -y -qq zlib1g-dev >/dev/null
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY *.go ./
RUN CGO_ENABLED=1 go build -trimpath -ldflags '-s -w -extldflags "-static"' -o /soci-indexer .

FROM public.ecr.aws/docker/library/debian:bookworm-slim
RUN apt-get update -qq && apt-get install -y -qq --no-install-recommends ca-certificates curl >/dev/null && rm -rf /var/lib/apt/lists/*
COPY --from=build /soci-indexer /usr/local/bin/soci-indexer
ENTRYPOINT []
