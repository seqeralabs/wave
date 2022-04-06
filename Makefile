all: pack/layers/layer.tar.gzip

clean:
	rm -rf pack/layers/*
	rm -rf .layer/opt/goofys/goofys
	./gradlew clean

.layer/opt/goofys/goofys:
	mkdir -p .layer/opt/goofys
	wget -O .layer/opt/goofys/goofys https://nf-xpack.s3.amazonaws.com/goofys/v0.25.0.beta/goofys
	chmod +x .layer/opt/goofys/goofys

.layer/opt/juicefs/juicefs:
	mkdir -p .layer/opt/juicefs
	rm -rf juicefs*
	./make-juicefs.sh
	mv juicefs .layer/opt/juicefs

pack/layers/layer.tar.gzip: .layer/opt/goofys/goofys .layer/opt/juicefs/juicefs
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
