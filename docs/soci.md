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
the first pull of new layers waits.

The indexing is warmed up when the container is requested: the request launches, in the background,
the indexing of the eligible layers of the requested platform, and returns without waiting for it.
When the pull comes later than the indexing takes (e.g. a task pulled after the VM boots), the first
pull is not held at all; otherwise it waits only for the remaining time. The pull launches the
indexing of any layer still without zTOC (e.g. another platform, or a failed warm-up); a layer
already being indexed is never launched twice. When the indexing fails or times out, the image is served as it
is, without SOCI index. Wave caches the manifest it served for the container token, so later pulls
with the same token also get the image without SOCI index; request a new token to get it once the
layers are indexed. Layers added by Wave (e.g. Fusion) are not indexed.

**Freeze and mirror containers** (pulled from their registry). Wave pushes a SOCI-enabled copy
of the image index to the sibling tag `<tag>-soci`, once the container exists and its layers are
indexed. The original tag is never changed. The request returns the `-soci` tag only once it
exists, otherwise the original tag: the first request of a container is not SOCI-enabled. A container
with no layer to index has no `-soci` tag. The target registry must accept OCI image indexes with artifact manifests.

## Rejected requests

A request with `soci: true` is rejected (HTTP 400) when:
- SOCI is not enabled in the Wave deployment (`wave.soci.enabled`);
- the container format is Singularity;
- an ephemeral container is requested by digest, since its manifest cannot be altered.

A freeze or mirror container requested by digest is not rejected, but gets no `-soci` tag.

## Client compatibility

Clients without the SOCI snapshotter ignore the SOCI index and pull the image as usual. This was checked with
Docker (classic and containerd image store), containerd, Podman and Apptainer.

nerdctl 2.1 or later and `ctr` don't pass the image labels the SOCI snapshotter needs, so they fall back to
a full pull. Docker with the SOCI snapshotter and nerdctl 2.0 load the image lazily.
