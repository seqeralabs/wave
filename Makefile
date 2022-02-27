all: pack/layers/layer.tar.gzip

clean:
	rm -f pack/layers/*

.layer/opt/goofys/goofys:
	wget -O .layer/opt/goofys/goofys https://github.com/kahing/goofys/releases/download/v0.24.0/goofys
	chmod +x .layer/opt/goofys/goofys

pack/layers/layer.tar.gzip: .layer/opt/goofys/goofys
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
	./gradlew jib
