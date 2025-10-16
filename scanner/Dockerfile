ARG version=0.65.0
ARG oras_version=1.3.0
FROM aquasec/trivy:${version}
# Disable zipbomb detection in unzip utility. Container layers can have high compression ratios
# that trigger false positives. The scanner needs to examine all compressed artifacts without
# restrictions as part of security scanning operations.
ENV UNZIP_DISABLE_ZIPBOMB_DETECTION=TRUE
RUN apk add --no-cache curl unzip bash
# Re-declare ARGs after FROM to use them in RUN commands
ARG oras_version=1.3.0
ARG TARGETARCH
RUN curl -LO "https://github.com/oras-project/oras/releases/download/v${oras_version}/oras_${oras_version}_linux_${TARGETARCH}.tar.gz" \
    && tar -zxf oras_${oras_version}_linux_${TARGETARCH}.tar.gz \
    && mv oras /usr/local/bin/ \
    && rm oras_${oras_version}_linux_${TARGETARCH}.tar.gz

COPY scan.sh /usr/local/bin/scan.sh
RUN chmod +x /usr/local/bin/scan.sh

ENTRYPOINT []
