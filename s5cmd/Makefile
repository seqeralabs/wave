version ?= 2.2.2

build:
	docker buildx build \
			--push \
			--platform linux/amd64,linux/arm64 \
			--build-arg version=${version} \
			--tag public.cr.seqera.io/wave/s5cmd:v${version} \
			.
