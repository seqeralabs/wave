config ?= compileClasspath

ifdef module
mm = :${module}:
else
mm =
endif


compile:
	 ./gradlew assemble
     ./zio-sergio

check:
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
