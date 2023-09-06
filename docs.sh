#!/bin/bash
#
#  Copyright (c) 2023, Seqera Labs.
#
#  This Source Code Form is subject to the terms of the Mozilla Public
#  License, v. 2.0. If a copy of the MPL was not distributed with this
#  file, You can obtain one at http://mozilla.org/MPL/2.0/.
#
#  This Source Code Form is "Incompatible With Secondary Licenses", as
#  defined by the Mozilla Public License, v. 2.0.
#

action=${1:-build}
if [[ $action = 'serve' ]]; then
  docker run --rm -it -p 8001:8000 -v ${PWD}:/docs squidfunk/mkdocs-material
else
  docker run --rm -it -v ${PWD}:/docs squidfunk/mkdocs-material ${action}
fi
