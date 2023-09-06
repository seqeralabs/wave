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

export JVM_OPTS='-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8010'
. ./run.sh
