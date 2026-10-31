---
title: SOCI lazy loading
---

Wave can attach a [SOCI](https://github.com/awslabs/soci-snapshotter) index (index manifest v2) to a container,
so that hosts running the SOCI snapshotter start it before the whole image is downloaded and fetch file data on demand.

## Feasibility results

Spike run on 31 Oct 2026 with soci-snapshotter v0.16.1 and containerd v2.4.2. The test image was
`python:3.12-slim`, converted with `soci convert --platform linux/arm64 --platform linux/amd64`. The converted
index lists the two image manifests first and the two SOCI index manifests last.

| Client | Pulls and runs the real image | Selected manifest |
|---|---|---|
| Docker Desktop 27 (containerd image store) | yes, both `--platform linux/arm64` and `linux/amd64` | image manifest |
| Docker 27.5 (classic `overlay2` store, dind) | yes | image manifest (`RepoDigests` = index digest) |
| containerd 2.4 + nerdctl, `overlayfs` snapshotter (no SOCI) | yes | image manifest |
| Podman 5.8 | pull resolves to the arm64 image manifest | image manifest (exec failed in the nested-container sandbox, the control image too) |
| Apptainer 1.4.4 | yes (`exec docker://…`) | image manifest |

No client selected a SOCI index descriptor, although it carries the same `platform` as the image manifest.

`soci convert` indexed a 29.8 MB and a 12.1 MB layer in 0.7 s. It skipped the 251-byte layer
(below `min-layer-size` 10 MiB).

The golden fixtures used by the tests live in `src/test/resources/soci/`:
- the original Docker Hub index and amd64 manifest;
- the converted index, annotated manifest and SOCI index manifest, kept as raw bytes so their digests match.
