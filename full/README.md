# Wave (full) with Docker Compose

Runs Wave with container **build**, **mirror** and **scan** enabled on a single Docker host, without Kubernetes.

When no `wave.build.k8s` setting is defined, Wave runs its jobs (buildkit, singularity, skopeo, trivy) as
sibling containers on the host Docker daemon, through the mounted `/var/run/docker.sock`.

## Requirements

- One Linux host with Docker Engine and Docker Compose. Size it for Wave (4 GB, 2 CPUs) **plus** the
  concurrent build jobs: build and scan containers run without CPU or memory limits.
- The Wave server image, e.g. `cr.seqera.io/enterprise/wave/server:<version>` (`docker login cr.seqera.io` first).
- A container registry for the built images (`WAVE_BUILD_REPO`) and the build cache (`WAVE_BUILD_CACHE`),
  with push credentials in `config.yml` under `wave.registries.<registry host>`.
- PostgreSQL and Redis: included in `docker-compose.yml`.
- A reverse proxy for TLS in front of port 9090. Wave does not terminate TLS.

## Run

1. Fill in `wave.env`: Wave and Platform URLs, build and cache repositories, registry credentials.
2. Pick an absolute host directory for the Wave data (workspace, build logs, locks, scan reports):

    ```
    export WAVE_DATA=/data/wave
    export WAVE_IMAGE=cr.seqera.io/enterprise/wave/server:<version>
    docker compose up -d --build
    ```

3. Check it: `curl http://localhost:9090/service-info`

The `Dockerfile` extends the Wave image with the `docker` CLI, which the published image does not include.

## Constraints

- **`WAVE_DATA` is mounted at the same absolute path in the Wave container and on the host.** Job containers
  bind-mount their work directory as `-v $workDir:$workDir`, and the host daemon resolves that path against
  the host filesystem. Do not change one side of the mount only.
- **Single Docker daemon.** Wave tracks jobs with `docker inspect` on its daemon, so all replicas must run
  on the same host. Scale up the host, not out.
- **Security.** Access to the Docker socket is root-equivalent on the host, and build containers run with
  `--privileged`. Run Wave on a dedicated host or VM, do not expose PostgreSQL and Redis outside it, and
  restrict job containers egress to the registries and package repositories they need (e.g. with rules on
  the `DOCKER-USER` iptables chain for the default bridge subnet).
- **Multi-platform builds** run every platform on the same host: building `linux/arm64` on an `amd64`
  host (or the opposite) requires QEMU emulation (`docker run --privileged --rm tonistiigi/binfmt --install all`)
  and is slow.
- Jobs exceeding the build/scan timeout are killed by Wave (`docker kill`).
