version ?= $(shell cat VERSION)
registry ?= public.cr.stage-seqera.io/wave
image = ${registry}/condasplit:${version}
# checksum of the tracked image sources, docs excluded, stored as an image label
source = $(shell git ls-files -z -- . ':!:*.md' | LC_ALL=C sort -z | xargs -0 sha256sum | sha256sum | cut -c1-64)
label = io.seqera.condasplit.source

all: build

build:
	docker buildx build \
			--push \
			--platform linux/amd64,linux/arm64 \
			--label ${label}=${source} \
			--tag ${image} \
			.

# build and push the image when the tag is missing, skip it when the tag has the same sources,
# fail otherwise: tags are immutable, so changed sources need a new VERSION
release:
	@found=$$(docker buildx imagetools inspect ${image} --format '{{ index (index .Image "linux/amd64").Config.Labels "${label}" }}' 2>&1) || { \
		case "$$found" in \
			*": not found"*) exec $(MAKE) build ;; \
			*) echo "$$found" >&2; exit 1 ;; \
		esac; \
	}; \
	if [ "$$found" = "${source}" ]; then \
		echo "${image} already exists with the same sources, skipping the build"; \
	else \
		echo "${image} already exists with different sources (${label}: '$$found', expected: '${source}'), bump VERSION to publish them" >&2; \
		exit 1; \
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
