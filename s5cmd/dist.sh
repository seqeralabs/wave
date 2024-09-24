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

arch=$(uname -m)

case $arch in
    x86_64|amd64)
        echo "https://github.com/peak/s5cmd/releases/download/v$1/s5cmd_$1_Linux-64bit.tar.gz"
        ;;
    aarch64|arm64)
	echo "https://github.com/peak/s5cmd/releases/download/v$1/s5cmd_$1_Linux-arm64.tar.gz"
        ;;
    *)
        echo "Unknown architecture: $arch"
        ;;
esac
