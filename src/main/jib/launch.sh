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
  io.seqera.Application
