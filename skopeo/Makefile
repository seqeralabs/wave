skopeo_version ?= 1.19.0
fusion_version ?= 2.4.13

build:
	docker buildx build \
			--push \
			--platform linux/amd64,linux/arm64 \
			--build-arg SKOPEO_VERSION=${skopeo_version} \
            --build-arg FUSION_VERSION=${fusion_version} \
			--tag public.cr.seqera.io/wave/skopeo:v${skopeo_version}-${fusion_version} \
			.
