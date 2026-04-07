#!/bin/bash

#
# Copyright 2025, Seqera Labs
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#

if [ $# -ne 1 ]; then
  echo "Specify the library to publish"
  exit 1
fi

if [[ ! -n "$AWS_REGION" && ! -n "$AWS_DEFAULT_REGION" ]]; then
  export AWS_DEFAULT_REGION="eu-west-1"
fi

NAME=$1
VERSION=$(cat VERSION)
PUBLISH_REPO_URL=${PUBLISH_REPO_URL:-s3://maven.seqera.io/snapshots}
BASE_URL=${PUBLISH_REPO_URL#s3://}
BUCKET=$(dirname $BASE_URL)
BASE_PATH=$(basename $BASE_URL)
KEY=$BASE_PATH/io/seqera/$NAME/$VERSION/$NAME-$VERSION.pom

echo "Publishing '$NAME-$VERSION' to $BUCKET/$KEY"
ret=$(aws s3api head-object --bucket $BUCKET --key $KEY 2>&1) && {
  # already exists => just a message
  echo "NOTE: Library $NAME-$VERSION already exist - skipping publishing"
 } || {
  if [[ $ret == *"Not Found"* ]]; then
    # the lib does not exist => publish it
    ./gradlew $NAME:publishMavenPublicationToSeqeraRepository
  else
    # print the error message
    echo $ret >&2
    exit 1
  fi
}