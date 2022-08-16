docker run \
  --rm \
  -v $PWD:$PWD \
  -w $PWD \
  -e AWS_ACCESS_KEY_ID \
  -e AWS_SECRET_ACCESS_KEY \
  -it \
  gcr.io/kaniko-project/executor:latest \
  --dockerfile Dockerfile_test --no-push
