#!/bin/bash
# Tag and and push the the GitHub repo and Docker images
#
# - The tag is taken from the `version` in the `build.gradle` file
# - The tagging is enabled using putting the string `[release]` in the
#   commit comment
# - Use the string `[force release]` to override existing tag/images
#
set -e
set -x
SED=sed
[[ $(uname) == Darwin ]] && SED=gsed
# check for [release] [force] and [enterprise] string in the commit comment
FORCE=${FORCE:-$(git show -s --format='%s' | $SED -rn 's/.*\[(force)\].*/\1/p')}
RELEASE=${RELEASE:-$(git show -s --format='%s' | $SED -rn 's/.*\[(release)\].*/\1/p')}
REMOTE=https://$GITHUB_TOKEN:x-oauth-basic@github.com/seqeralabs/tower-reg.git
ENTERPRISE=${ENTERPRISE:-$(git show -s --format='%s' | $SED -rn 's/.*\[(enterprise)\].*/\1/p')}
MARKETPLACE=${ENTERPRISE:-$(git show -s --format='%s' | $SED -rn 's/.*\[(marketplace)\].*/\1/p')}

if [[ $RELEASE ]]; then
  # take the version from the `build.gradle` file
  TAG=v$(cat build.gradle | sed -En 's@version *= *"([0-9]+\.[0-9]+\.[0-9]+)"@\1@p')
  [[ $FORCE == 'force' ]] && FORCE='-f'
  # tag repo
  git tag $TAG $FORCE
  git push $REMOTE $TAG $FORCE
  # build and push image
  make pack
  # push it
  make push
fi
