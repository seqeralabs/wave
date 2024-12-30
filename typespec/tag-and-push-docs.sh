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

set -e
set -x
SED=sed
[[ $(uname) == Darwin ]] && SED=gsed

RELEASE=${RELEASE:-$(git show -s --format='%s' | $SED -rn 's/.*\[(release)\].*/\1/p')}
if [[ $RELEASE ]]; then
  npm install -g @typespec/compiler

  cd typespec

  tsp install

  tsp compile .

  docker build -t 195996028523.dkr.ecr.eu-west-1.amazonaws.com/nf-tower-enterprise/wave-docs:$VERSION .

  docker push 195996028523.dkr.ecr.eu-west-1.amazonaws.com/nf-tower-enterprise/wave-docs:$VERSION
fi
