ARG version
FROM aquasec/trivy:${version}
ARG oras_version
RUN apk add --no-cache curl unzip
RUN curl -LO "https://github.com/oras-project/oras/releases/download/v${oras_version}/oras_${oras_version}_linux_amd64.tar.gz" \
    && tar -zxf oras_${oras_version}_linux_amd64.tar.gz \
    && mv oras /usr/local/bin/ \
    && rm oras_${oras_version}_linux_amd64.tar.gz
ENTRYPOINT [""]
