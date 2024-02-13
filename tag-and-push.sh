#!/bin/bash
#
#  Wave, containers provisioning service
#  Copyright (c) 2023-2024, Seqera Labs
#
#  This program is free software: you can redistribute it and/or modify
#  it under the terms of the GNU Affero General Public License as published by
#  the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
#
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU Affero General Public License for more details.
#
#  You should have received a copy of the GNU Affero General Public License
#  along with this program.  If not, see <https://www.gnu.org/licenses/>.
#

# Tag and and push the the GitHub repo and Docker images
#
# - The tag is taken from the `VERSION` file in the project root
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
REMOTE=https://oauth:$GITHUB_TOKEN@github.com/${GITHUB_REPOSITORY}.git
ENTERPRISE=${ENTERPRISE:-$(git show -s --format='%s' | $SED -rn 's/.*\[(enterprise)\].*/\1/p')}
MARKETPLACE=${MARKETPLACE:-$(git show -s --format='%s' | $SED -rn 's/.*\[(marketplace)\].*/\1/p')}

if [[ $RELEASE ]]; then
  # take the version from the `build.gradle` file
  TAG=v$(cat VERSION)
  [[ $FORCE == 'force' ]] && FORCE='-f'
  # tag repo
  git tag $TAG $FORCE
  git push $REMOTE $TAG $FORCE
  # build and push the container
  ./gradlew jib
  # build and push enterprise
  ./gradlew -PjibRepo=195996028523.dkr.ecr.eu-west-1.amazonaws.com/nf-tower-enterprise/wave:$TAG jib
  # publish release notes
  gh release create $TAG --generate-notes
fi
