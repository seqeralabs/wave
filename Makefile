config ?= compileClasspath

ifdef module
mm = :${module}:
else
mm =
endif

all: pack/layers/layer.tar.gzip

clean:
	rm -rf pack/layers/*
	rm -rf .layer/opt/tini
	rm -rf .layer/opt/goofys
	rm -rf .layer/opt/geesefs
	rm -rf .layer/opt/fusion/fusionfs
	./gradlew clean

.layer/opt/geesefs:
	mkdir -p .layer/opt/geesefs
	wget -O .layer/opt/geesefs/geesefs https://github.com/yandex-cloud/geesefs/releases/download/v0.31.3/geesefs-linux-amd64
	chmod +x .layer/opt/geesefs/geesefs

.layer/opt/tini:
	mkdir -p .layer/opt/tini
	wget -P .layer/opt/tini https://github.com/krallin/tini/releases/download/v0.19.0/tini-static-amd64
	wget -P .layer/opt/tini https://github.com/krallin/tini/releases/download/v0.19.0/tini-static-muslc-amd64
	chmod +x .layer/opt/tini/tini-*

pack/layers/layer.tar.gzip: .layer/opt/geesefs .layer/opt/tini
	mkdir -p pack/layers
	./make-tar.sh

pack: clean pack/layers/layer.tar.gzip


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

#
# Show dependencies try `make deps config=runtime`, `make deps config=google`
#
deps:
	./gradlew -q ${mm}dependencies --configuration ${config}
