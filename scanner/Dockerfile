ARG version
FROM aquasec/trivy:${version}
ARG oras_version
RUN apk add --no-cache curl unzip bash
RUN curl -LO "https://github.com/oras-project/oras/releases/download/v${oras_version}/oras_${oras_version}_linux_amd64.tar.gz" \
    && tar -zxf oras_${oras_version}_linux_amd64.tar.gz \
    && mv oras /usr/local/bin/ \
    && rm oras_${oras_version}_linux_amd64.tar.gz

COPY scan.sh /usr/local/bin/scan.sh
RUN chmod +x /usr/local/bin/scan.sh

ENTRYPOINT ["/usr/local/bin/scan.sh"]
