version ?= 0.1.0
zfs_version ?= 0.15.5

build:
	docker buildx build \
			--push \
			--platform linux/amd64,linux/arm64 \
			--build-arg version=${zfs_version} \
			--tag cr.seqera.io/public/zerofs:v${version}-zfs-${zfs_version} \
			.
