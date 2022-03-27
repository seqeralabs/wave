export XREG_ARCH=$(uname -m)
# create config file from env var
< application-example.yml \
  DOCKER_USER=pditommaso \
  DOCKER_PAT=${DOCKER_PAT} \
  QUAY_USER=pditommaso \
  QUAY_PAT=${QUAY_PAT} \
  envsubst > application-dev.yml
export MICRONAUT_CONFIG_FILES="classpath:application.yml,file:application-dev.yml"

./gradlew run
