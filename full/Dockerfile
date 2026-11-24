# Wave image able to run build, mirror and scan jobs with Docker.
#
# Without 'wave.build.k8s' settings Wave launches its jobs with the 'docker' CLI,
# which the published Wave image does not include. This adds the static CLI binary;
# the Docker daemon is the host one, reached through the mounted '/var/run/docker.sock'.
ARG WAVE_IMAGE
ARG DOCKER_CLI_IMAGE=docker:28.5.1-cli

FROM ${DOCKER_CLI_IMAGE} AS docker-cli

FROM ${WAVE_IMAGE}
COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker
