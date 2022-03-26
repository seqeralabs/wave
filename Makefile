all: pack/layers/layer.tar.gzip

clean:
	rm -rf pack/layers/*
	rm -rf .layer/opt/fusion/fusionfs
	./gradlew clean

.layer/opt/fusion/fusionfs:
	gh release download -p fusionfs -R seqeralabs/fusionfs -D .layer/opt/fusion/
	chmod +x .layer/opt/fusion/fusionfs

pack/layers/layer.tar.gzip: .layer/opt/fusion/fusionfs
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
	./push.sh
