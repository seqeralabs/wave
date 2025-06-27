singularity_version ?= 4.2.1-r4
fusion_version ?= 2.4.13

build:
	docker buildx build \
			--push \
			--platform linux/amd64,linux/arm64 \
			--build-arg SINGULARITY_VERSION=${singularity_version} \
            --build-arg FUSION_VERSION=${fusion_version} \
			--tag public.cr.seqera.io/wave/singularity:v${singularity_version}-${fusion_version} \
			.
