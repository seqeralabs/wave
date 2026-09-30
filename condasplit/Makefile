version ?= $(shell cat VERSION)
registry ?= public.cr.stage-seqera.io/wave

all: build

build:
	docker buildx build \
			--push \
			--platform linux/amd64,linux/arm64 \
			--tag ${registry}/condasplit:${version} \
			.

# build and push the image unless the tag exists, tags are immutable
release:
	@if docker manifest inspect ${registry}/condasplit:${version} > /dev/null 2>&1; then \
		echo "${registry}/condasplit:${version} already exists, skipping the build"; \
	else \
		$(MAKE) build; \
	fi

# run the unit tests as root, so that the owner tests are not skipped
test:
	docker run --rm -v ${CURDIR}:/src -w /src golang:1.27 sh -c 'go test ./... && cd frontend && go test ./...'

# single-arch image loaded into the local docker as condasplit:dev, it is never pushed
load:
	docker buildx build \
			--load \
			--tag condasplit:dev \
			.

.PHONY: all build release test load
