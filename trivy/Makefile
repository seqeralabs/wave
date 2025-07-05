trivy_version ?= 0.63.0
fusion_version ?= 2.4.13

build:
	docker buildx build \
			--push \
			--platform linux/amd64,linux/arm64 \
			--build-arg TRIVY_VERSION=${trivy_version} \
            --build-arg FUSION_VERSION=${fusion_version} \
			--tag public.cr.seqera.io/wave/trivy:v${trivy_version}-${fusion_version} \
			.
