#!/bin/bash
#
#  Wave, containers provisioning service
#  Copyright (c) 2023, Seqera Labs
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

action=${1:-build}
if [[ $action = 'serve' ]]; then
  docker run --rm -it -p 8001:8000 -v ${PWD}:/docs squidfunk/mkdocs-material
else
  docker run --rm -it -v ${PWD}:/docs squidfunk/mkdocs-material ${action}
fi
