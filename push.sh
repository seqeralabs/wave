# creds file
set +x
< application-example.yml \
  DOCKER_USER=pditommaso \
  DOCKER_PAT=${DOCKER_PAT} \
  QUAY_USER=pditommaso \
  QUAY_PAT=${QUAY_PAT} \
  envsubst > application-prod.yml
# docker login
docker login -u pditommaso -p ${DOCKER_PASSWORD}
./gradlew jib