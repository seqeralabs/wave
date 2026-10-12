version ?= 4.2.1-r4

build:
	docker buildx build \
			--push \
			--platform linux/amd64,linux/arm64 \
			--build-arg version=${version} \
			--tag public.cr.seqera.io/wave/singularity:v${version} \
			.
