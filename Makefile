all: pack/layers/layer.tar.gzip

clean:
	rm -rf pack/layers/*
	rm -rf .layer/opt/goofys/goofys
	./gradlew clean

.layer/opt/goofys/goofys:
	wget -O .layer/opt/goofys/goofys https://nf-xpack.s3.amazonaws.com/goofys/v0.25.0.beta/goofys
	chmod +x .layer/opt/goofys/goofys

with-tar: .layer/opt/goofys/goofys
	mkdir -p pack/layers
	./make-tar.sh
	
pack/layers/layer.tar.gzip: .layer/opt/goofys/goofys
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
