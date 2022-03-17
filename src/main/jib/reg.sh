# Launch backend server
export CLIENT_ARCH=amd64
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
  ${JAVA_OPTS} \
  -cp /app/resources:/app/classes:/app/libs/* \
  io.seqera.Application
