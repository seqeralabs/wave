FROM ubuntu
ARG version
ADD dist.sh /dist.sh
RUN apt update && apt install -y curl tar  \
  && curl -L $(sh -x /dist.sh ${version}) | tar -zx \
  && cp s5cmd /usr/local/bin/s5cmd \

