FROM gcr.io/kaniko-project/executor:latest AS knk

FROM amazoncorretto:17.0.4

COPY --from=knk /kaniko/executor /kaniko/executor
COPY --from=knk /kaniko/docker-credential-gcr /kaniko/docker-credential-gcr
COPY --from=knk /kaniko/docker-credential-ecr-login /kaniko/docker-credential-ecr-login
COPY --from=knk /kaniko/docker-credential-acr-env /kaniko/docker-credential-acr-env
COPY --from=knk /kaniko/ssl/certs/ /kaniko/ssl/certs/
COPY --from=knk /kaniko/.docker /kaniko/.docker
ENV HOME=/root
ENV USER=root
ENV PATH="\$PATH:/kaniko"
ENV SSL_CERT_DIR=/kaniko/ssl/certs
ENV DOCKER_CONFIG=/kaniko/.docker/
ENV DOCKER_CREDENTIAL_GCR_CONFIG=/kaniko/.config/gcloud/docker_credential_gcr_config.json
WORKDIR /workspace
