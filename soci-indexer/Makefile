version ?= v$(shell cat VERSION)
# non-prod default, the Wave release publishes to public.cr.seqera.io/wave, see build.yml
registry ?= public.cr.stage-seqera.io/wave
image = ${registry}/soci-indexer:${version}
# checksum of the tracked image sources, docs excluded, stored as an image label
source = $(shell git ls-files -z -- . ':!:*.md' | LC_ALL=C sort -z | xargs -0 sha256sum | sha256sum | cut -c1-64)
label = io.seqera.soci-indexer.source

all: build

build:
	docker buildx build \
			--push \
			--platform linux/amd64,linux/arm64 \
			--label ${label}=${source} \
			--tag ${image} \
			.

# fail when the VERSION tag is published with different sources: tags are immutable, so changed
# sources need a new VERSION
check:
	@found=$$(docker buildx imagetools inspect ${image} --format '{{ index (index .Image "linux/amd64").Config.Labels "${label}" }}' 2>&1) || { \
		case "$$found" in \
			*": not found"*) echo "${image} is not published yet"; exit 0 ;; \
			*) echo "$$found" >&2; exit 1 ;; \
		esac; \
	}; \
	if [ "$$found" = "${source}" ]; then \
		echo "${image} already exists with the same sources"; \
	else \
		echo "${image} already exists with different sources (${label}: '$$found', expected: '${source}'), bump VERSION to publish them" >&2; \
		exit 1; \
	fi

# build and push the image when the tag is missing, skip it when the tag has the same sources
release: check
	@if docker buildx imagetools inspect ${image} >/dev/null 2>&1; then \
		echo "skipping the build of ${image}"; \
	else \
		$(MAKE) build; \
	fi

# the soci ztoc library needs cgo and a static zlib, so the tests run in a Linux container
test:
	docker run --rm -v ${CURDIR}:/src -w /src golang:1.26-bookworm \
		bash -c 'apt-get update -qq && apt-get install -y -qq zlib1g-dev >/dev/null && go vet ./... && go test ./...'

# image loaded into the local docker as soci-indexer:dev, it is never pushed
load:
	docker buildx build \
			--load \
			--tag soci-indexer:dev \
			.

.PHONY: all build check release test load
