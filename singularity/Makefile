version ?= 1.0.0

build:
	docker buildx build \
			--push \
			--platform linux/amd64,linux/arm64 \
			--tag public.cr.seqera.io/wave/singularity:v${version} \
			.
