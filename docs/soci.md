---
title: SOCI lazy loading
---

Wave can attach a [SOCI](https://github.com/awslabs/soci-snapshotter) index (index manifest v2) to a container,
so that hosts running the SOCI snapshotter start it before the whole image is downloaded and fetch file data on demand.

## How it works

Set `soci: true` in the container request (`wave --soci` with the Wave CLI). Wave builds the zTOC
(table of contents) of each gzip layer of at least 10 MiB with a `soci-indexer` job. A zTOC is built
once per layer digest and stored in the `wave.soci.repo` repository, so it is reused by every
image sharing that layer. Indexing starts when the container is requested, and takes seconds
(the 5 eligible layers of `python:3.12`, 394 MB, were indexed in 11.5 s).

**Ephemeral containers** (pulled through Wave). Wave adds the SOCI index manifest to the image
index it serves. As for a container build, the manifest request is held until the zTOCs of all the
eligible layers are available, at most `wave.soci.await-timeout`. A layer is indexed once, so only
the first pull of new layers waits. When the indexing fails or times out, the image is served as it
is, without SOCI index. Wave caches the manifest it served for the container token, so later pulls
with the same token also get the image without SOCI index; request a new token to get it once the
layers are indexed. Layers added by Wave (e.g. Fusion) are not indexed. Containers requested by digest cannot be
altered and are rejected with `soci: true`.

**Freeze and mirror containers** (pulled from their registry). Wave pushes a SOCI-enabled copy
of the image index to the sibling tag `<tag>-soci`, once the container exists and its layers are
indexed. The original tag is never changed. The request returns the `-soci` tag only once it
exists, otherwise the original tag: the first request of a container is not SOCI-enabled. A container
with no layer to index has no `-soci` tag. The target registry must accept OCI image indexes with artifact manifests.

Clients without the SOCI snapshotter ignore the SOCI index and pull the image as usual.

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
