docker run \
  --rm \
  -v $PWD:$PWD \
  -w $PWD \
  -e AWS_ACCESS_KEY_ID \
  -e AWS_SECRET_ACCESS_KEY \
  -it \
  wave-base \
   /kaniko/executor --dockerfile Dockerfile_test --no-push
