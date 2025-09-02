config ?= runtimeClasspath

ifdef module
mm = :${module}:
else
mm = :wave-app:
endif


compile:
	 ./gradlew assemble

check:
	./gradlew check

image:
	./gradlew jibDockerBuild

push:
	# docker login
	docker login -u pditommaso -p ${DOCKER_PASSWORD}
	./gradlew jib


gen-api:
	./gradlew generateApiCode

gen-docs: 
	./gradlew generateSwaggerUI

#
# Show dependencies try `make deps config=runtime`, `make deps config=google`
#
deps:
	./gradlew -q ${mm}dependencies --configuration ${config}
