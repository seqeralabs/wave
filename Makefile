all: build/layers/layer.tar.gzip

clean:
	rm -f build/layers/*

.layer/opt/goofys/goofys:
	wget -O .layer/opt/goofys/goofys https://github.com/kahing/goofys/releases/download/v0.24.0/goofys
	chmod +x .layer/opt/goofys/goofys

build/layers/layer.tar.gzip: .layer/opt/goofys/goofys
	mkdir -p build/layers
	./make-tar.sh

image:
	./gradlew jibDockerBuild

push:
	./gradlew jib
