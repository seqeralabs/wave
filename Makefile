all: pack/layers/layer.tar.gzip

clean:
	rm -rf pack/layers/*
	rm -rf .layer/opt/goofys/goofys
	rm -rf .layer/opt/fusion/fusionfs
	./gradlew clean

.layer/opt/goofys/goofys:
	mkdir -p .layer/opt/goofys
	wget -O .layer/opt/goofys/goofys https://nf-xpack.s3.amazonaws.com/goofys/v0.25.0.beta/goofys
	chmod +x .layer/opt/goofys/goofys

.layer/opt/fusion/fusionfs:
	mkdir -p .layer/opt/fusion
	gh release download -p fusionfs -R seqeralabs/fusionfs -D .layer/opt/fusion/
	chmod +x .layer/opt/fusion/fusionfs

pack/layers/layer.tar.gzip: .layer/opt/fusion/fusionfs
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
