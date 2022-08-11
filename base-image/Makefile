registry ?= 195996028523.dkr.ecr.eu-west-1.amazonaws.com
tag ?= 0.1.3
platform ?= linux/amd64

build: FORCE
	docker buildx build \
		--platform ${platform} \
		--push \
		--tag ${registry}/wave/kaniko:${tag} \
		.

FORCE: ; 
