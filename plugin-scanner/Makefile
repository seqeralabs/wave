version ?= 0.65.0

build:
	docker buildx build \
			--push \
			--platform linux/amd64,linux/arm64 \
			--build-arg version=${version} \
			--tag hrma017/trivy-oras:v${version} \
			.
