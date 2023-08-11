#!/bin/bash

if [ $# -ne 1 ]; then
  echo "Specity the library to publish"
  exit 1
fi

NAME=$1
VERSION=$(cat $NAME/VERSION)
PUBLISH_REPO_URL=${PUBLISH_REPO_URL:-s3://maven.seqera.io/snapshots}
BASE_URL=${PUBLISH_REPO_URL#s3://}
BUCKET=$(dirname $BASE_URL)
BASE_PATH=$(basename $BASE_URL)
KEY=$BASE_PATH/io/seqera/$NAME/$VERSION/$NAME-$VERSION.pom

echo "Publishing '$NAME-$VERSION' to $BUCKET/$KEY"
aws s3api head-object --bucket $BUCKET --key $KEY &> /dev/null

# Check the exit status of the command
if [ $? -eq 0 ]; then
  echo "NOTE: Library $NAME-$VERSION already exist - skipping publishing"
else
## go ahead with the publishing
echo ./gradlew $NAME:publishMavenPublicationToSeqeraRepositoryRepository
fi
