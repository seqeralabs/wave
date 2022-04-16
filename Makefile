all: pack/layers/layer.tar.gzip

clean:
	rm -rf pack/layers/*
	rm -rf .layer/opt/tini
	rm -rf .layer/opt/goofys
	rm -rf .layer/opt/fusion/fusionfs
	./gradlew clean

.layer/opt/goofys:
	mkdir -p .layer/opt/goofys
	wget -P .layer/opt/goofys https://nf-xpack.s3.amazonaws.com/goofys/v0.25.0.beta/goofys
	chmod +x .layer/opt/goofys/goofys

.layer/opt/tini:
	mkdir -p .layer/opt/tini
	wget -P .layer/opt/tini https://github.com/krallin/tini/releases/download/v0.19.0/tini-static-amd64
	wget -P .layer/opt/tini https://github.com/krallin/tini/releases/download/v0.19.0/tini-static-muslc-amd64
	chmod +x .layer/opt/tini/tini-*

.layer/opt/juicefs:
	mkdir -p .layer/opt/juicefs
	rm -rf juicefs*
	./make-juicefs.sh
	mv juicefs .layer/opt/juicefs

pack/layers/layer.tar.gzip: .layer/opt/juicefs .layer/opt/tini
	mkdir -p pack/layers
	./make-tar.sh

pack: clean pack/layers/layer.tar.gzip

buildInfo:
	./gradlew buildInfo

compile:
	 ./gradlew assemble

check: pack/layers/layer.tar.gzip
	./gradlew check

image:
	./gradlew jibDockerBuild

push:
	# docker login
	docker login -u pditommaso -p ${DOCKER_PASSWORD}
	./gradlew jib
