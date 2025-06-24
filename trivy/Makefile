version ?= 0.63.0

build:
	docker buildx build \
			--push \
			--platform linux/amd64,linux/arm64 \
			--build-arg version=${version} \
			--tag public.cr.seqera.io/wave/trivy:v${version} \
			.
