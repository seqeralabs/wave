all: pack/layers/layer.tar.gzip

clean:
	rm -rf pack/layers/*
	rm -rf .layer/opt/fusion/fusionfs
	./gradlew clean

.layer/opt/fusion/fusionfs:
	gh release download -p fusionfs -R seqeralabs/fusionfs -D .layer/opt/fusion/
	chmod +x .layer/opt/fusion/fusionfs

with-tar: .layer/opt/fusion/fusionfs
	mkdir -p pack/layers
	./make-tar.sh
	
pack/layers/layer.tar.gzip: .layer/opt/fusion/fusionfs
	./gradlew assemble -x test
	./make-jar.sh build/libs/tower-reg-*-all.jar

pack: clean pack/layers/layer.tar.gzip

compile:
	 ./gradlew assemble

check: pack/layers/layer.tar.gzip
	./gradlew check

image:
	./gradlew jibDockerBuild

push:
	./gradlew jib
