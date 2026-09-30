version ?= v1
registry ?= public.cr.stage-seqera.io/wave

all: build

build:
	docker buildx build \
			--push \
			--platform linux/amd64,linux/arm64 \
			--tag ${registry}/conda-layers:${version} \
			.

# run the unit tests as root, so that the owner tests are not skipped
test:
	docker run --rm -v ${CURDIR}:/src -w /src golang:1.27 go test ./...

# single-arch image loaded into the local docker as conda-layers:dev, it is never pushed
load:
	docker buildx build \
			--load \
			--tag conda-layers:dev \
			.

.PHONY: all build test load
