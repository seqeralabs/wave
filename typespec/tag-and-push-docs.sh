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

npm install -g @typespec/compiler

cd typespec
tsp install
tsp compile .

TAG=$(cat ../VERSION)

docker build -t docker.io/hrma017/wave/openapi:$TAG .
echo "Build docker.io/wave/openapi:$TAG"

docker push docker.io/hrma017/wave/openapi:$TAG
echo "Pushed docker.io/hrma017/wave/openapi:$TAG"
