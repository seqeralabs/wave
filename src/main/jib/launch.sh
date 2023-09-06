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

# Launch backend server
[ "$WAVE_JVM_OPTS" ] && echo "Detected WAVE_JVM_OPTS=$WAVE_JVM_OPTS"
exec java \
  -Dfile.encoding=UTF-8 \
  -Dcom.sun.security.enableAIAcaIssuers=true \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.math=ALL-UNNAMED \
  --add-opens java.base/java.net=ALL-UNNAMED \
  --add-opens java.base/java.text=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED \
  --add-opens java.base/java.util.concurrent=ALL-UNNAMED \
  --add-opens=java.base/java.io=ALL-UNNAMED \
  --add-opens=java.base/java.nio=ALL-UNNAMED \
  --add-opens=java.base/java.util=ALL-UNNAMED \
  ${WAVE_JVM_OPTS} \
  -cp /app/resources:/app/classes:/app/libs/* \
  io.seqera.wave.Application
