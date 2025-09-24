ARG version
FROM aquasec/trivy:${version}
RUN apk add --no-cache curl unzip
RUN curl -LO "https://github.com/oras-project/oras/releases/download/v1.1.0/oras_1.1.0_linux_amd64.tar.gz" \
    && tar -zxf oras_1.1.0_linux_amd64.tar.gz \
    && mv oras /usr/local/bin/ \
    && rm oras_1.1.0_linux_amd64.tar.gz
ENTRYPOINT [""]
