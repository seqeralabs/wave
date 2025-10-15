trivy_version ?= 0.65.0
oras_version ?= 1.3.0
build:
	docker buildx build \
			--push \
			--platform linux/amd64,linux/arm64 \
			--build-arg version=${trivy_version} \
			--build-arg oras_version=${oras_version} \
			--tag public.cr.seqera.io/wave/scanner:v${trivy_version}-oras-${oras_version} \
			.
