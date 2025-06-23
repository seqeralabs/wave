buildkit_version ?= 0.22.0-rootless
fusion_version ?= 2.4.13

build:
	docker buildx build \
			--push \
			--platform linux/amd64,linux/arm64 \
			--build-arg BUILDKIT_VERSION=${buildkit_version} \
			--build-arg FUSION_VERSION=${fusion_version} \
			--tag public.cr.seqera.io/wave/buildkit:v${buildkit-version}-${fusion_version} \
			.
