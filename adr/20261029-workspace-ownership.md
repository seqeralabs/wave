# Workspace Ownership of Wave Records

## Summary

Wave records (container requests, builds, mirrors and scans) are readable today by anyone holding their id. This ADR lets a record created by an authenticated Seqera Platform user be marked private, so that only users with access to the Platform workspace it was created in can read it. It covers four areas:

1. Ownership, visibility and access segregation: who owns a record, when it is private, who can read it
2. Data structure changes: the owner and visibility fields on the records, and the build id
3. View pages: how `/view/…` pages apply the access check
4. API changes: a new read API iteration with optional authentication, while the current API stays untouched initially

Records are public unless the client asks for `private`, so anonymous usage, the community registry, existing links and every client version in use keep working unchanged.

## Context

The identity stored on each record today:

| Record | Table | Identity stored today |
|---|---|---|
| `WaveContainerRecord` | `wave_request` | user, `workspaceId`, `towerEndpoint` |
| `WaveBuildRecord` | `wave_build` | `userId`, `userName`, `userEmail` |
| `MirrorResult` | `wave_mirror` | `userId`, `userName`, `userEmail` |
| `WaveScanRecord` | `wave_scan` | none, only `buildId`, `mirrorId` or `requestId` |

This is not enough to decide who owns a record:

- A user id alone can't be checked: ids are unique only within one Platform, and builds and mirrors don't store which Platform issued them, or the workspace.
- A build id is a hash of content and target (`ContainerHelper.makeContainerId`), so all requesters share one build record, which stores only the first requester.
- `ContainerController.getContainerImpl` fills a missing `towerEndpoint` with the default `towerEndpointUrl` before it handles anonymous requests. Anonymous request records therefore already carry the Seqera Cloud endpoint, so the presence of `towerEndpoint` doesn't mean a record is owned.
- `towerWorkspaceId` is sent by the client and isn't verified at submit. Platform only refuses it later, if Wave fetches credentials for that workspace.

Every request type already carries the caller's `PlatformId` (`BuildRequest.identity`, `MirrorRequest.identity`, `ScanRequest.identity`) at creation time. Most of it is dropped before the record is saved.

Making records private by default isn't an option: every Nextflow and Wave CLI release in use today would lose access to the pages of their own builds, and many users pin Nextflow versions or can't upgrade quickly.

## Decision

### 1. Ownership, Visibility and Access Segregation

#### 1.1 The owner is the Platform workspace

**Decision:** A record created by a request that carries a valid Platform token (its `PlatformId` is not `PlatformId.NULL`) is owned by:

```
tenant = "${towerEndpoint}:${workspaceId ?: 'user:' + userId}"
```

with `towerEndpoint` normalised (see 2.3). A request without a workspace belongs to the user's personal workspace, so its owner is the user. Anonymous requests have no owner.

**Rationale:**
- Platform's unit of access is the workspace: its members share pipelines, runs and credentials, so they should share the Wave records those produce
- Different workspaces and organisations never see each other's records, on Seqera Cloud as on an enterprise Platform
- The endpoint is part of the owner because workspace and user ids are unique only within one Platform
- Platform remains the source of truth for membership: Wave asks it and never stores member lists

#### 1.2 Visibility is explicit and defaults to public

**Decision:**
- The submit request gets an optional `visibility` field, `public` or `private`
- When the field is absent, the record is `public`
- `private` takes effect only on an authenticated request. On an anonymous request it is ignored and the record is public
- Builds to the community repository (`defaultPublicRepository`) are always public: their names are deterministic and meant to be shared
- Visibility is set when the record is created and never changes. Making a private build public means building it again as public, which mostly hits the layer cache
- Scans have no visibility of their own: they take the owner and visibility of their build, mirror or container request. A scan from `POST /view/scans` has no parent, so it is public

**Rationale:**
- Clients released before this change send nothing, so their behaviour doesn't change
- Each client opts in on its own release schedule: Platform first, Nextflow and the Wave CLI when they are ready

#### 1.3 Ownership rule

**Decision:**

```
private(record) = record.owner?.towerEndpoint != null
               && record.visibility == "private"
```

Never use the existing `WaveContainerRecord.towerEndpoint`, or `userId`, to infer ownership. Records created before this change have no `owner`, so they are public, with no backfill and no migration.

#### 1.4 Access check

**Decision:**

```
record not private                                   → allow
no token (Authorization header or cookie)            → 401
caller = userInfo(record.owner.towerEndpoint, token)
    invalid                                          → 401
record.owner.workspaceId == null
    caller.id == record.owner.userId                 → allow
record.owner.workspaceId in workspaces(caller)       → allow
otherwise                                            → 403
```

- The Platform asked is always the one stored on the record. Clients send no endpoint on the read path, so a request can't point Wave at another host
- `workspaces(caller)` comes from Platform's `GET /user/{userId}/workspaces` on the record's endpoint, called with the caller's own token. It returns the caller's workspaces with their `orgId`, and Platform allows a caller to list only their own. This needs a new `TowerClient` method and response model
- The token is validated exactly as presented. The submit path's `TowerConnector.sendAsync1` swaps in a newer token from `JwtAuthStore` and refreshes on a 401. The access path calls Platform without the store lookup and without refresh: a 401 from Platform is a 401 from Wave
- The user-info and workspace results are each cached for 60 seconds, under keys separate from the submit path's `userInfo` cache entries
- A record's logs, conda lock file, SPDX file and scan logs follow the record

**Rationale:**
- Refreshing would let an expired or leaked token keep working for up to the 30 days `JwtAuthStore` keeps it
- 60 seconds is the delay before a revoked token or a removed membership takes effect

#### 1.5 Workspace verified at submit for private requests

**Decision:** When a request asks for `private` and names a workspace, Wave checks that the caller belongs to it, with the same cached `workspaces(caller)` call, and rejects the request with 403 if not.

**Rationale:** without the check, a caller could create records owned by a workspace they don't belong to, or compute the build id of another workspace's private build.

### 2. Data Structure Changes

#### 2.1 Owner and visibility on every record

**Decision:** Add one shared `owner` object and a `visibility` field to the four records:

```
owner {
  towerEndpoint   // normalised, see 2.3
  userId
  workspaceId     // null for a personal workspace
  orgId           // reserved
}
visibility        // "public" | "private"
```

| Record | Source of the owner |
|---|---|
| `WaveContainerRecord` | the request identity |
| `WaveBuildRecord` | `BuildRequest.identity` in `fromEvent` |
| `MirrorResult` | `MirrorRequest.identity` in `from` |
| `WaveScanRecord` | `ScanRequest.identity`, carried through `ScanEntry` |

`owner` is written only when the request identity is authenticated. `SubmitContainerTokenRequest` (v1 and v2) gets the optional `visibility` field, and the build, mirror and scan requests carry it to their records.

**Rationale:**
- Each table stores its record in a `data JSONB` column, so no DDL is needed. Records without `owner` deserialise as today
- `workspaceId` is the key the access check uses (1.4), so it must be on every record, not only on container requests
- `orgId` costs nothing to reserve and allows organisation-level settings later. The workspace listing called for private requests (1.5) already returns it, so private records can get it at no extra cost

#### 2.2 Owner in the build id, for private builds only

**Decision:** `makeContainerId` adds the owner to the hashed attributes when the build is private:

```
attrs.containerFile, condaFile, platform, repository, buildContext, containerConfig
+ attrs.tenant = tenant    // 1.1, only when visibility == "private"
```

**Rationale:**
- A private build gets a record of its own, so it belongs to exactly one workspace. Without this, the first workspace to build some content would own the record, and other workspaces that reuse the build couldn't read it
- Public and anonymous build ids don't change, and neither does the existing cache
- Mirror ids are already unique per request, so mirrors need no change

**Cost:** content built privately is built once per workspace that requests it. Layers are still cached by content, so most of a duplicate build is cache hits.

#### 2.3 One canonical endpoint string

**Decision:** Normalise the endpoint once at submit, after `patchPlatformEndpoint`: lowercase the scheme and host, drop the default port and the trailing slash. Use the same form as the key the pairing lookup accepts.

**Rationale:** the endpoint is now part of the owner. It decides the build id, the owner and the Platform a token is checked against. Two spellings of the same Platform would otherwise become two owners.

### 3. View Pages

**Decision:**

| Page | Behaviour |
|---|---|
| `GET /view/builds/{id}`, `/view/containers/{token}`, `/view/mirrors/{id}`, `/view/scans/{id}` | Public record: renders as today. Private record: access check (1.4) using Platform's `JWT` cookie |
| Build list and scan list pages (`allBuilds`, `allScans`) | Private entries the viewer can't read are left out of the list |
| `POST /view/scans`, `GET /view/inspect` | Unchanged. They create public records or show public images |

- **The token is Platform's own `JWT` cookie.** Platform can share it with sibling subdomains through `TOWER_AUTH_COOKIE_DOMAIN`. With Platform and Wave under one parent domain, a signed-in user's browser sends it to Wave automatically
- **No valid cookie: 401.** The page says "This build belongs to a Seqera Platform workspace. Sign in to Seqera Platform and reload this page"
- **Valid cookie, not a member: 403.** The page says the record belongs to a workspace the viewer doesn't have access to
- **A counter for refused views**, by endpoint and status, so a client sending `private` too widely shows up in monitoring

**Rationale:** links Wave and Platform have already handed out (`detailsUri`, build notification emails, `ChildRefs`, Platform's build, scan and mirror URLs, the Data Studios build link) keep pointing at `/view/…`. Public records open as today; private records open for a member of the record's workspace who is signed in to that Platform.

### 4. API Changes

#### 4.1 The current API is untouched initially

**Decision:** The current read endpoints keep working exactly as today and return every record, private ones included, to any caller:

| Current endpoint |
|---|
| `GET /container-token/{token}` |
| `GET /v1alpha2/container/{requestId}`, `/v1alpha2/container/{requestId}/status` |
| `GET /v1alpha1/builds/{buildId}`, `/logs`, `/status`, `/condalock` |
| `GET /v1alpha1/mirrors/{mirrorId}`, `/logs` |
| `GET /v1alpha1/scans/{scanId}`, `/spdx`, `/logs` |

Once the new API ships, they are marked deprecated with a `Deprecation` header, and removed on an announced date.

**Rationale:** Nextflow, the Wave CLI, Platform and scripts poll these endpoints today, many with no token. Changing them would break pinned client versions.

#### 4.2 A new read API iteration

**Decision:** Add a new version of the read endpoints, covering the same resources and sub-resources as 4.1:

- `Authorization: Bearer` is optional
- Public records are returned without a token, as today
- Private records go through the access check (1.4): 401 without a valid token, 403 for a non-member
- Responses have the same shape as the current endpoints, so clients move by changing the path and sending their token

The submit endpoints are unchanged apart from the optional `visibility` field (2.1). Anonymous submits are still accepted.

**Rationale:**
- Clients move on their own schedule, and nothing breaks for those that don't
- The same check backs the pages and the API, so a record has one access rule everywhere

## Implementation Plan

Each pull request can be merged and released on its own. The first three change no behaviour, because no client sends `private` yet.

| # | Pull request | Scope | Size |
|---|---|---|---|
| 1 | Record the owner | Endpoint normalisation (2.3). `owner` on the four records (2.1). Scan identity carried through `ScanEntry`. Serialisation tests for records without `owner` | S |
| 2 | Visibility field | `visibility` on the submit request, v1 and v2, and on mirror requests. Ignored when anonymous or for the community repository (1.2). Stored on records; scans inherit from their parent | S |
| 3 | Owner in build id | `makeContainerId` takes the owner for private builds only (2.2). Tests that public and anonymous ids don't change | S |
| 4 | Access check | Service returning allow, 401 or 403 (1.3, 1.4). `TowerClient` user-info call without token store or refresh; `GET /user/{userId}/workspaces` call and response model; 60s caches. Workspace check at submit (1.5) | M |
| 5 | View pages | Read the `JWT` cookie. Access check on the record pages, filtering on the list pages, 401 and 403 templates, refused-view counter (3) | M |
| 6 | New read API | New API version with optional bearer auth (4.2). `Deprecation` header on the current API (4.1) | M |

Outside Wave:

| Component | Change | When |
|---|---|---|
| Platform | Confirm the `JWT` cookie reaches the Wave domain in each environment (`TOWER_AUTH_COOKIE_DOMAIN`, `SameSite`) | Before PR 5 ships |
| Platform | Send `visibility: private` on the requests it submits | After PR 5 is live |
| Nextflow | Expose `wave.visibility`; read through the new API with the token | Any time after PRs 2 and 6 |
| Wave CLI | Expose `--visibility`; read through the new API with the token | Any time after PRs 2 and 6 |

Rollout: PRs 1 to 6 change nothing visible, because no record is private yet; test on staging with a private request sent by hand. The first visible change is Platform sending `private`, a Platform-side switch that can be turned off without a Wave release. Removing the current read API is a later, announced step.

### Tests

- An anonymous request with no endpoint stores no `owner` and a public record, even though `towerEndpoint` defaults to Seqera Cloud
- `private` on an anonymous request, or on the community repository, gives a public record and an unchanged build id
- The same Containerfile sent as private from two workspaces, or from the same workspace id on two endpoints, gives two build ids; from one workspace, or sent as public, it gives one
- A private request naming a workspace the caller doesn't belong to is rejected with 403
- Access check: a token from another endpoint gets 401; an expired token gets 401 and isn't refreshed; a member of the record's workspace passes; a valid user outside the workspace gets 403; on a personal-workspace record only the same user passes; results are cached for 60 seconds
- The current API still returns private records; the new API applies the check
- Records written before this change still load and are public

## Consequences

- **The current API returns private records** to anyone holding the id until it is removed. Private records are protected on every path only after that, so the deprecation period should be short and announced with a date
- **Privacy depends on clients opting in.** A client that should send `private` and doesn't creates a public record
- **Platforms on another domain** can't share the cookie with Wave, so their users see the 401 page on private records until a browser sign-in flow exists
- **One more Platform call per viewer.** Reading a private record costs a user-info call and a workspace listing on the record's Platform, each cached for 60 seconds. Private submits with a workspace pay the workspace listing too
- **Scans of existing images** are keyed by image and digest, so they are shared by everyone who scans the same image. They contain only vulnerability data. Adding the owner to that scan id is a small follow-up
- **Existing records stay public** for as long as they are kept

## Open Questions

1. Does `GET /user/{userId}/workspaces` list every workspace the user can access, including those an organisation owner reaches through the organisation rather than as a participant? If not, organisation owners would get 403 on records of workspaces they administer
2. Is the `JWT` cookie scoped to a domain that includes Wave in Cloud, stage and dev, and is it `SameSite=Lax`, so links from email work?
3. Should Platform send `private` for Data Studios builds? It has no page that shows them today
4. The version name of the new read API, and the deprecation date of the current one
5. Should the new API's `/status` endpoints also require a token for private records, or stay open since they expose only build state?
