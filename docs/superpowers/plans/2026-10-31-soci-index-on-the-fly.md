# SOCI v2 Index On-the-Fly Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make any container served by Wave lazy-loadable by the SOCI snapshotter, including images Wave did not build, by generating per-layer zTOCs once (cached by layer digest) and attaching a SOCI index manifest v2 to the image.

**Architecture:** One per-layer indexing job (new `JobSpec.Type.SociIndex`) produces a zTOC per gzip layer and pushes it to a Wave-owned OCI repository (`wave.soci.repo`) as a tiny artifact tagged by layer digest. A pure `SociManifestAssembler` turns "image manifest + zTOCs" into the SOCI v2 documents. *Ephemeral* containers get those documents injected on the fly by `ContainerAugmenter` at pull time. *Durable* containers (freeze/mirror) are pulled straight from the user's registry, bypassing Wave, so for them the same documents are pushed to a sibling tag `<tag>-soci` by a job.

**Tech Stack:** Groovy/Micronaut (Wave), Spock; a small Go tool `soci-indexer` built on `github.com/awslabs/soci-snapshotter/ztoc` and `go-containerregistry` (push only); existing K8s/Docker job runners; Wave's build registry (ECR in prod). No S3 bucket, and no dependency on the blob cache.

**Spec:** this document, section "Design". No separate spec exists. Wire format verified against `awslabs/soci-snapshotter@main`: `soci/soci_index.go`, `soci/soci_convert.go`.

## Design

### How a request reaches Wave today
| Kind | Condition (`ContainerRequest`) | Client pulls from | Manifest rewritten today? |
|---|---|---|---|
| Ephemeral, augmented | `containerConfig && !freeze` | Wave proxy (`wave.seqera.io/wt/<token>/…`) | Yes, `ContainerAugmenter.resolve` |
| Ephemeral, plain | no `containerConfig` | Wave proxy | No, upstream manifest is cached as-is (`ContainerAugmenter.groovy:180`) |
| Ephemeral, built | `containerFile`/`packages`, `!freeze` | Wave proxy, image in Wave build repo | No |
| Durable | `durable() = freeze \|\| mirror` (`ContainerRequest.groovy:64`) | **User's registry directly.** `makeResponseV2` returns `data.containerImage` | n/a, Wave is not on the pull path |

### SOCI v2 wire format to emit (exact values)
- Image index gets one extra descriptor per indexed platform, **appended after** the image entries:
  `mediaType: application/vnd.oci.image.manifest.v1+json`, `artifactType: application/vnd.amazon.soci.index.v2+json`, same `platform` as the image, annotation `com.amazon.soci.image-manifest-digest: <A2>`.
- Image manifest A gets rewritten to A2 by adding annotation `com.amazon.soci.index-digest: <S>`. The manifest descriptor in the index carries the same annotations.
- SOCI index manifest S: OCI image manifest, `artifactType` `application/vnd.amazon.soci.index.v2+json`, config mediaType `application/vnd.amazon.soci.index.v2+json` with the empty-JSON config `{}` (`sha256:44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a`, size 2). `layers` holds one entry per zTOC: `mediaType: application/octet-stream`, annotations `com.amazon.soci.image-layer-digest`, `com.amazon.soci.image-layer-mediaType`, `com.amazon.soci.span-size`. No `subject` field.
- Docker v2 manifests, configs and indexes are converted to OCI media types, as `soci convert` does: registries reject mixed Docker/OCI types.
- A single-manifest image (no index) is wrapped in a new OCI image index.

### Decisions
1. **Opt-in per request.** New request field `soci: Boolean` (default false), gated by `wave.soci.enabled`. SOCI rewrites digests, so it is never turned on implicitly.
2. **Unit of caching = layer digest.** zTOCs are content-addressed and shared across images and repositories (base layers are indexed once). Only gzip layers of at least `min-layer-size` are indexed; others are omitted from S, which is legal (the snapshotter pulls them in full).
3. **All-or-nothing per manifest.** Wave emits A2+S only when every eligible layer has a zTOC. Partial indexes would change the digest again as more zTOCs land, churning caches.
4. **Index early.** Indexing is triggered at `POST /container-token`, before the VM even boots. Indexing takes about 10–30 s for 1–2 GB images; VM boot takes about 60 s. So the first pull is usually already lazy. Until the index is ready, the plain manifest is served and its tag-path cache entry gets a short TTL.
5. **Durable images go to a sibling tag `<tag>-soci`.** The original tag keeps its digest, so build-cache checks (`getImageDigest` + `loadBuildSucceed`) and mirror `--preserve-digests` keep working. The response returns the `-soci` tag **only if it already exists**. Otherwise it returns the original tag and starts the conversion, so a pull can never hit a missing tag.
6. **One assembler for both paths.** The Groovy `SociManifestAssembler` builds the documents. The Go tool only builds zTOCs (`index`) and uploads prepared blobs and manifests (`push`). No containerd is involved anywhere.

## Global Constraints
- Span size `4194304` (4 MiB) and min layer size `10485760` (10 MiB), matching soci defaults. Both are configurable (`wave.soci.span-size`, `wave.soci.min-layer-size`).
- zTOC storage: repository `wave.soci.repo` (default `<wave.build.repo>/soci-ztoc`; never `wave.build.cache`, which may be an S3 path). Each zTOC is one OCI artifact: the zTOC is its only layer blob (`application/octet-stream`), the config is `{}`, and its artifactType is `application/vnd.seqera.wave.ztoc.v1+json`. It is tagged `ztoc-<span-size>-<layer-digest-hex>`, with annotations `io.seqera.wave.ztoc.layer-digest`, `io.seqera.wave.ztoc.layer-mediaType`, `io.seqera.wave.ztoc.digest`, `io.seqera.wave.ztoc.size` and `io.seqera.wave.ztoc.span-size`. The registry is the source of truth, Redis is only a lookup cache, and cleanup uses registry lifecycle rules. The lifecycle expiry on `soci-ztoc` must be longer than the Redis TTL (30d, so e.g. 90d), otherwise Redis can point at a deleted zTOC.
- Ineligible layers (< min size, not gzip) are decided from the manifest alone: no job runs and nothing is stored for them.
- Annotation keys and media types: exactly as listed in "SOCI v2 wire format". Never invented.
- Requests pinned by digest (`image@sha256:…`) with `soci: true` on an ephemeral container → 400 `UNSUPPORTED`, same rule as augmentation (`ContainerAugmenter.groovy:188`). Singularity format with `soci: true` → 400.
- `wave.soci.enabled=false` (the default) → the field is ignored and behaviour is byte-identical to today.
- **Registries:** tests, spikes, dev and stage use `public.cr.stage-seqera.io` (test images, `soci convert` fixtures, freeze/mirror `--build-repo`, the `soci-indexer` image for non-prod, and the non-prod `wave.soci.repo`, e.g. `public.cr.stage-seqera.io/wave/soci-ztoc`). Only prod uses `public.cr.seqera.io` / the prod build registry. Never push test artifacts to a prod registry.
- Follow Wave conventions: `@CompileStatic`, AGPL header, Spock `*Test.groovy`, jobs via `JobService`/`JobFactory`/`JobHandler`.

## Review Focus
1. **Range requests through the proxy.** `RegistryProxyService.handleRequest` caches blob responses by path only. A 206 partial body would be cached and served to a later full GET. Test in Task 2.
2. **Clients without SOCI** (Docker classic store, containerd without SOCI, Podman, Apptainer) must ignore the extra index entry. Gate in Task 0. The assembler test pins "SOCI entries appended last" (Task 4).
3. **Stale tag-path manifest cache.** `RegistryProxyController.manifestForPath` caches the resolved manifest under the request path for `wave.storage.cache.duration` (1h). A plain manifest cached while indexing is pending must expire after `pending-cache-duration`. Test in Task 6.
4. **Ineligible layers** (zstd, uncompressed, < 10 MiB, an image with zero eligible layers): they are skipped. With zero eligible layers, Wave serves the plain image and never emits an empty S. Tests in Tasks 3 and 4.
5. **Multi-arch images:** only the requested platform(s) are indexed and rewritten; other index entries stay byte-identical. Test in Task 4.

---

### Task 0: Feasibility spike (gate, no production code)

- [ ] **Step 1: Docker + SOCI on one Flatcar VM.** Docker with the containerd image store, `soci-snapshotter-grpc` as a proxy plugin, `daemon.json` snapshotter `soci`. Pull an image converted with `soci convert` and pushed to `public.cr.stage-seqera.io/wave/soci-spike:<name>`. Pass criterion: the container starts before the full image is fetched (`soci-snapshotter` journal shows lazy fetches).
- [ ] **Step 2: Client compatibility.** Pull the same converted image with Docker 27 (classic store), Docker with the containerd store but no SOCI, Podman 5, and Apptainer 1.3. Pass criterion: all pull and run the real image, none selects the SOCI descriptor.
- [ ] **Step 3: Golden fixtures.** Run `soci convert` on a small multi-layer image. Save its index, A2, S and one zTOC under `src/test/resources/soci/` for the assembler tests.
- [ ] **Step 4: Record the results** in `docs/soci.md`. Stop the plan if Step 1 or Step 2 fails.

### Task 1: Request flag and config

**Files:**
- Modify: `wave-api/src/main/java/io/seqera/wave/api/SubmitContainerTokenRequest.java` (field `Boolean soci`, fluent `withSoci`)
- Modify: `src/main/groovy/io/seqera/wave/service/request/ContainerRequest.groovy` (carry `soci`, add to `create(...)`)
- Modify: `src/main/groovy/io/seqera/wave/controller/ContainerController.groovy` (validation, pass through)
- Create: `src/main/groovy/io/seqera/wave/configuration/SociConfig.groovy`
- Test: `src/test/groovy/io/seqera/wave/controller/ContainerControllerTest.groovy`

**Interfaces:**
- Produces: `SubmitContainerTokenRequest.soci` / `withSoci(Boolean)` in `wave-api`, which needs a Wave release before Task 9 (the CLI) can use it. `ContainerRequest.soci: boolean`, readable at pull time via `RoutePath.request.soci`. `SociConfig` with `enabled` (default `false`), `spanSize` (`4MiB`), `minLayerSize` (`10MiB`), `pendingCacheDuration` (`30s`), `indexerImage`, `jobTimeout` (`10m`), `repo` (`wave.soci.repo`, default `${wave.build.repo}/soci-ztoc`).

- [ ] **Step 1: Write failing tests** `should reject soci with digest-pinned ephemeral image` (expects 400 containing `UNSUPPORTED`), `should reject soci with singularity format`, and `should ignore soci when disabled` (the persisted `ContainerRequest.soci == false`).
- [ ] **Step 2: Run** `./gradlew test --tests 'io.seqera.wave.controller.ContainerControllerTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** the field, config and validation in `ContainerController.makeRequestData`.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `feat(soci): add soci request flag and config`.

### Task 2: Range-safe blob proxying

**Files:**
- Modify: `src/main/groovy/io/seqera/wave/core/RegistryProxyService.groovy:246-257` (`handleRequest`)
- Modify: `src/main/groovy/io/seqera/wave/controller/RegistryProxyController.groovy` (stream path keeps upstream `206` + `Content-Range`)
- Test: `src/test/groovy/io/seqera/wave/core/RegistryProxyServiceTest.groovy`

- [ ] **Step 1: Write failing test** `should not cache ranged blob responses`. Given a stubbed upstream returning 206 for `Range: bytes=0-99`, the next un-ranged GET on the same path calls upstream again. Redirect responses (`location != null`) stay cached even with Range.
- [ ] **Step 2: Run** `./gradlew test --tests 'io.seqera.wave.core.RegistryProxyServiceTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** In `handleRequest`, bypass `cache.getOrCompute` when the headers contain `Range` and the response is not a redirect. Forward the status and `Content-Range` on the streaming path.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `fix(proxy): never cache partial blob responses`.

Note: with the blob cache enabled, the first ranged read waits for the full layer transfer (`BlobCacheServiceImpl.retrieveBlobCache`). Later reads go to S3/CloudFront, which honour Range. This is accepted for v1 and documented in `docs/soci.md`.

### Task 3: `soci-indexer` Go tool and image

**Files:**
- Create: `soci-indexer/` (Go module `github.com/seqeralabs/wave/soci-indexer`), `soci-indexer/main.go`, `soci-indexer/index.go`, `soci-indexer/push.go`, `soci-indexer/Dockerfile`, `soci-indexer/index_test.go`
- Modify: `.github/workflows/` build to publish `public.cr.stage-seqera.io/wave/soci-indexer:<version>` on every merge (used by dev/stage and tests), and `public.cr.seqera.io/wave/soci-indexer:<version>` on release only (prod)

**Interfaces:**
- Produces CLI `soci-indexer index --layer <file> --digest <sha256:…> --media-type <type> --span <bytes> --min-size <bytes> --repo <wave.soci.repo>`. It pushes the zTOC blob, then the artifact manifest under tag `ztoc-<span>-<layer-hex>`, last, so a resolvable tag means the entry is complete. It uses docker-config credentials, mounted the same way build jobs mount theirs. It prints one JSON line on stdout: `{"layerDigest":"…","ztocDigest":"…","ztocSize":N,"skipped":false,"reason":null}`.
- Produces CLI `soci-indexer push --target <repo> --plan <file.json>`. The plan file lists `blobs: [{digest, size, source}]` (source is `docker://<repo>@<digest>`, which uses a cross-repo blob mount when on the same registry and a copy otherwise, or inline base64) and `manifests: [{reference, mediaType, body}]`, pushed in order. It uploads only blobs missing in the target (HEAD first) and uses docker-config credentials.

- [ ] **Step 1: Write failing tests**
  - `TestIndexGzipLayer`: build a 12 MiB gzip tar fixture → output not skipped. Unmarshalling the zTOC via `ztoc.Unmarshal` lists the same file paths as the tar.
  - `TestSkipSmallLayer`: a 1 MiB gzip tar → `skipped:true, reason:"min-size"`.
  - `TestSkipNonGzip`: a `…tar+zstd` media type → `skipped:true, reason:"compression"`.
- [ ] **Step 2: Run** `cd soci-indexer && go test ./...`. Expected: FAIL.
- [ ] **Step 3: Implement** `index` with `ztoc.NewBuilder("wave-soci-indexer").BuildZtoc(file, span)` + `ztoc.Marshal`, pushing with `go-containerregistry` (`remote.WriteLayer` + `remote.Put` of the artifact manifest). Implement `push` with `go-containerregistry` `remote.WriteLayer`/`remote.Put` using raw bytes, so Wave's digests are preserved byte-for-byte.
- [ ] **Step 4: Run.** Expected: PASS. Also compare the fixture's file list against `soci ztoc info` output from Task 0.
- [ ] **Step 5: Commit** `feat(soci): add soci-indexer tool`.

### Task 4: `SociManifestAssembler` (pure)

**Files:**
- Create: `src/main/groovy/io/seqera/wave/service/soci/SociManifestAssembler.groovy`
- Create: `src/main/groovy/io/seqera/wave/service/soci/ZtocEntry.groovy` (`@Canonical`: `layerDigest`, `layerMediaType`, `ztocDigest`, `ztocSize`, `spanSize`, `skipped`)
- Test: `src/test/groovy/io/seqera/wave/service/soci/SociManifestAssemblerTest.groovy`

**Interfaces:**
- Produces:
  - `static SociDocs assemble(String imageManifestJson, Map<String,ZtocEntry> ztocs, long spanSize)`. `SociDocs` (`@Canonical`) holds `manifestJson`, `manifestDigest`, `sociIndexJson`, `sociIndexDigest`, `configJson` = `{}`, and `ztocs` = the entries referenced. Returns `null` when no layer has a non-skipped zTOC.
  - `static String updateIndex(String indexJson, Map<String,SociDocs> byOriginalDigest, Map platformOf)`.
  - `static String wrapInIndex(SociDocs docs, Map platform)`.
  - `static boolean isComplete(String imageManifestJson, Map<String,ZtocEntry> ztocs, long minLayerSize)`: true when every gzip layer of at least the minimum size has an entry.

- [ ] **Step 1: Write failing tests** against the Task 0 golden fixtures:
  - `should produce soci index manifest matching soci convert`: the key set, `artifactType`, config descriptor (digest `sha256:44136fa…`, size 2) and layer annotations match the golden S. zTOC digests are compared from the fixture input.
  - `should annotate manifest with index digest`: `A2.annotations['com.amazon.soci.index-digest'] == S digest`, layers byte-identical in order.
  - `should append soci descriptor last with image-manifest-digest`.
  - `should convert docker media types to oci`: input Docker v2 manifest and list → outputs use the OCI index, manifest and config types, and layer media types map `vnd.docker.image.rootfs.diff.tar.gzip` → `vnd.oci.image.layer.v1.tar+gzip`.
  - `should leave other platforms untouched`: in an amd64+arm64 index where only amd64 is indexed, the arm64 entry is unchanged.
  - `should return null when no eligible layers`.
  - `isComplete is false when one eligible layer lacks a ztoc`.
- [ ] **Step 2: Run** `./gradlew test --tests 'io.seqera.wave.service.soci.SociManifestAssemblerTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** Use `JsonSlurper`/`JsonOutput` like `ContainerAugmenter.updateImageIndex`, and `RegHelper.digest` for digests.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(soci): add SOCI v2 manifest assembler`.

### Task 5: `SociIndexService` and indexing job

**Files:**
- Create: `src/main/groovy/io/seqera/wave/service/soci/SociIndexService.groovy`, `SociIndexServiceImpl.groovy`, `SociStateStore.groovy` (extends `AbstractStateStore<ZtocEntry>`, prefix `soci-ztoc/v1`, TTL `30d`), `SociIndexEntry.groovy` (job state: route, layer descriptor, status)
- Modify: `src/main/groovy/io/seqera/wave/service/job/JobSpec.groovy` (`Type.SociIndex` + `static JobSpec sociIndex(...)`), `JobFactory.groovy`, `JobService.groovy` + `JobServiceImpl.groovy` (`JobSpec launchSociIndex(SociIndexEntry entry, List<String> command)`), `impl/K8sJobOperation.groovy` and `impl/DockerJobOperation.groovy` (image `SociConfig.indexerImage`, ephemeral volume for the layer file)
- Test: `src/test/groovy/io/seqera/wave/service/soci/SociIndexServiceImplTest.groovy`

**Interfaces:**
- Consumes: `ZtocEntry`, `SociManifestAssembler.isComplete` (Task 4); `soci-indexer index` (Task 3); `RegistryProxyService.curl(route, headers)` for authenticated layer fetches (same as `BlobCacheServiceImpl.transferCommand`).
- Produces:
  - `Map<String,ZtocEntry> findZtocs(Collection<String> layerDigests)`: from the Redis store; on a miss it does `HEAD`/`GET` of manifest `<wave.soci.repo>:ztoc-<span>-<layer-hex>`, reads the annotations and repopulates Redis. A 404 means not indexed. It uses Wave's registry client and credentials (`RegistryProxyService`).
  - `void requestIndex(ContainerCoordinates image, ContainerPlatform platform, PlatformId identity)`: async. It resolves the platform manifest(s) via `ContainerAugmenter.getContainerSpec`, and for each eligible layer with no entry calls `storeIfAbsent(layerDigest)` then launches one job.
  - `JobHandler<SociIndexEntry>.onJobCompletion`, which parses the tool's JSON line into a `ZtocEntry` and stores it.
  - Job command: `bash -c 'set -o pipefail; <curl> > /work/layer && soci-indexer index --layer /work/layer …'`.

- [ ] **Step 1: Write failing tests**
  - `should launch one job per missing eligible layer`: a 3-layer manifest (one 1 MiB, one already in the store, one 50 MiB) → exactly one `launchSociIndex` call.
  - `should dedup concurrent requests for the same layer` (`storeIfAbsent`).
  - `should store entry on job completion`.
  - `should not store entry on job failure and allow retry`.
  - `should restore entry from registry tag annotations when Redis misses`: no job is launched. Also `should treat 404 tag as not indexed`.
- [ ] **Step 2: Run** `./gradlew test --tests 'io.seqera.wave.service.soci.SociIndexServiceImplTest'`. Expected: FAIL.
- [ ] **Step 3: Implement**, following `ContainerMirrorServiceImpl` and `BlobCacheServiceImpl` for the job lifecycle.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(soci): per-layer ztoc indexing job`.

### Task 6: Ephemeral containers: inject on the fly

**Files:**
- Modify: `src/main/groovy/io/seqera/wave/core/ContainerAugmenter.groovy`. A new `withSoci(SociIndexService, SociConfig)` and a soci step applied (a) after `augmentManifest` in `resolveImageIndex` and the single-manifest branch, and (b) in the `!containerConfig` branch (line 180), which today returns the digest unchanged.
- Modify: `src/main/groovy/io/seqera/wave/core/RegistryProxyService.groovy` (`scanner(...)` passes soci deps when `route.request?.soci`)
- Modify: `src/main/groovy/io/seqera/wave/controller/RegistryProxyController.groovy:229-247` (`manifestForPath`: short TTL when pending)
- Modify: `src/main/groovy/io/seqera/wave/storage/Storage.java` + `ManifestCacheStore.groovy` (`saveManifest(String path, DigestStore, Duration ttl)`; `AbstractStateStore.put(key, value, ttl)` already exists)
- Modify: `src/main/groovy/io/seqera/wave/controller/ContainerController.groovy` (call `requestIndex` at token time when `soci && !durable`; for ephemeral builds, call it on build completion)
- Test: `src/test/groovy/io/seqera/wave/core/ContainerAugmenterSociTest.groovy`, `src/test/groovy/io/seqera/wave/controller/RegistryProxyControllerSociTest.groovy`

**Interfaces:**
- Consumes: `SociManifestAssembler.assemble/updateIndex/wrapInIndex/isComplete`, `SociIndexService.findZtocs/requestIndex`.
- Produces: `ContainerDigestPair.sociPending: boolean` (true when soci was requested but zTOCs are incomplete). Stored objects under `"$registry/v2/$image/…"`, the same keys the augmenter uses today:
  - manifests A2 and S via `saveManifest`;
  - the `{}` config via `saveBlob(bytes)`;
  - each zTOC as a `DockerDigestStore` with location `docker://<wave.soci.repo>/blobs/<ztoc-digest>` (via `DigestStoreFactory`), so `RegistryProxyController.dockerRedirection` proxies it to the zTOC repository with Wave's credentials (registry → S3 redirect for ECR).

- [ ] **Step 1: Write failing tests**
  - `should inject soci docs into augmented index when ztocs complete`: the served index contains A2 (fusion layers + `com.amazon.soci.index-digest`) and an S entry. `storage.getManifest(".../manifests/<S>")` and `getBlob(".../blobs/<ztoc>")` are present.
  - `should inject soci docs for plain proxied image`: no `containerConfig`, so the digest changes from the upstream one.
  - `should serve plain manifest and request index when ztocs incomplete`: `sociPending == true` and `requestIndex` is called once.
  - `manifestForPath caches pending manifest for pendingCacheDuration`: TTL equals `30s`, not `1h`.
  - `should be byte-identical to today when soci not requested`.
- [ ] **Step 2: Run** `./gradlew test --tests '*ContainerAugmenterSociTest' --tests '*RegistryProxyControllerSociTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the above plus `--tests '*ContainerAugmenterTest' --tests '*RegistryProxyControllerTest'`. Expected: PASS (no regressions).
- [ ] **Step 5: Commit** `feat(soci): inject SOCI v2 index into ephemeral containers`.

### Task 7: Durable containers (freeze/mirror): push `<tag>-soci`

**Files:**
- Create: `src/main/groovy/io/seqera/wave/service/soci/SociPushService.groovy` (+ `Impl`, job handler; `JobSpec.Type.SociPush`)
- Modify: `ContainerController.groovy` (when `soci && durable`: if `getImageDigest("<target>-soci")` is non-null, return it as `targetImage`; otherwise return the original and schedule a push)
- Modify: build completion (`ContainerBuildServiceImpl` / `MultiPlatformBuildService` completion hook) and `ContainerMirrorServiceImpl.onJobCompletion` (`:134`) to call `SociPushService.schedule(targetImage, platform, identity)` when the originating request had `soci`
- Test: `src/test/groovy/io/seqera/wave/service/soci/SociPushServiceImplTest.groovy`, `ContainerControllerTest.groovy`

**Interfaces:**
- Consumes: `SociIndexService.requestIndex/findZtocs` (Task 5), `SociManifestAssembler` (Task 4), `soci-indexer push` (Task 3).
- Produces: `void schedule(String targetImage, ContainerPlatform platform, PlatformId identity)`. It waits until zTOCs are complete (re-checked on the indexing job's completion callback), writes the push-plan JSON (blobs: config `{}` + zTOCs as `docker://<wave.soci.repo>@<ztoc-digest>`; manifests: S, A2, then the index tagged `<tag>-soci`), and launches the push job using the build-repo credentials already used by build and mirror.

- [ ] **Step 1: Write failing tests**
  - `should return soci tag when it exists`.
  - `should return original tag and schedule push when soci tag missing`.
  - `should build push plan in order S, A2, index` with the tag `<tag>-soci`.
  - `should not change original tag digest`: the plan never references the original tag.
- [ ] **Step 2: Run** `./gradlew test --tests '*SociPushServiceImplTest' --tests '*ContainerControllerTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(soci): publish SOCI-enabled sibling tag for freeze and mirror`.

### Task 8: Docs, metrics, end-to-end check

**Files:**
- Create: `docs/soci.md` (user-facing: `soci` flag, ephemeral vs durable behaviour, first-pull caveat, registry requirements for durable images, the Task 0 results)
- Modify: `docs/api.md` (`soci` request field), `docs/install/reference.md` (`wave.soci.*` settings)
- Modify: `SociIndexServiceImpl` / `SociPushServiceImpl`: Micrometer `wave.soci.index.jobs` (tag `result`), `wave.soci.index.duration`, `wave.soci.manifest.served` (tag `state=ready|pending`)

- [ ] **Step 1: End-to-end on dev.** Request `ubuntu` and a 2 GB conda image with `soci:true` against dev Wave. Pull both twice on a Task 0 VM, with dev Wave configured with `wave.soci.repo=public.cr.stage-seqera.io/wave/soci-ztoc`. Pass criterion: the second pull logs SOCI lazy fetches, and `wave.soci.manifest.served{state=ready}` increments.
- [ ] **Step 2: Freeze end-to-end.** Run a freeze build with `soci:true` and `buildRepository=public.cr.stage-seqera.io/wave/soci-test`, and check that `<tag>-soci` exists. Request again: the response returns `-soci`, and `docker pull` of the original tag shows an unchanged digest.
- [ ] **Step 3: Commit** `docs(soci): document SOCI lazy loading`.

### Task 9: Wave CLI `--soci` option

The CLI lives in `wave/wave-cli/` (its own Gradle build). It depends on the **published** `io.seqera:wave-api` (`app/build.gradle:30`, currently `1.42.1`). So this task starts only after a Wave release that includes Task 1's `SubmitContainerTokenRequest.soci`.

**Files:**
- Modify: `wave-cli/app/build.gradle` (bump `io.seqera:wave-api` to the release that has `soci`)
- Modify: `wave-cli/app/src/main/java/io/seqera/wave/cli/App.java`:
  - add the option `@Option(names = {"--soci"}, description = "Enable SOCI lazy loading: attach a SOCI v2 index to the container (requires SOCI snapshotter on the pulling host)") private boolean soci;`, next to `--build-compression` (`:232`);
  - in `createRequest()` (`:446`), add `.withSoci(soci)`;
  - add the checks to `validateArgs()` (`:335`).
- Modify: `docs/cli/use-cases.md` (a "SOCI lazy loading" section: an ephemeral example plus a `--freeze --soci` example showing the `-soci` tag), `docs/cli/configuration.md` (option reference)
- Test: `wave-cli/app/src/test/groovy/io/seqera/wave/cli/AppTest.groovy`

**Interfaces:**
- Consumes: `SubmitContainerTokenRequest.withSoci(Boolean)` (Task 1, via the published `wave-api`).
- Behaviour: the CLI only sets the flag. The printed image is whatever the server returns in `targetImage`. For `--freeze`/`--mirror`, that is the `<tag>-soci` tag once it exists, and the original tag until then (Decision 5).

- [ ] **Step 1: Write failing tests** in `AppTest.groovy`, following `should set build compression estargz` (`:325`):
  - `should set soci flag`: `--image ubuntu --soci` → `createRequest().soci == true`.
  - `should not set soci by default`: `createRequest().soci == null || false`.
  - `should fail when soci with singularity`: `--singularity --soci` → `IllegalCliArgumentException` with a message containing `--soci`.
  - `should fail when soci with digest image and no freeze or mirror`: `--image ubuntu@sha256:… --soci` → `IllegalCliArgumentException`.
- [ ] **Step 2: Run** `cd wave-cli && ./gradlew test --tests 'io.seqera.wave.cli.AppTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** Add the option, `.withSoci(soci)`, and the two checks in `validateArgs()`. These mirror the server-side 400s from Global Constraints, so users get the error before a round-trip.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: End-to-end** against dev Wave:
  - `wave --image ubuntu:24.04 --soci` prints a `wave.seqera.io/wt/…` image whose index (via `crane manifest`) contains a descriptor with `artifactType: application/vnd.amazon.soci.index.v2+json`, once the zTOCs are ready.
  - `wave --image ubuntu:24.04 --soci --freeze --build-repo public.cr.stage-seqera.io/wave/soci-test`, run twice: the second run prints `<tag>-soci`.
- [ ] **Step 6: Commit** `feat(cli): add --soci option`, then add a `changelog.txt` entry.

## Out of scope
- Nextflow `wave.soci` config option and sched `lazyPull: soci` VM mode (separate plans; sched side in `sched-vm/.../VmLazyPullIgnition`).
- Piping zTOC build into the blob-cache transfer (`curl | tee | s5cmd`) to avoid a second layer read. This is an optimisation, to consider after the metrics from Task 8.
- SOCI prefetch artifacts (experimental upstream).
