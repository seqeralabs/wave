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
- Platform sends `private` for Data Studios builds: they are submitted with the studio user's token and the workspace (`SubmitContainerTokenRequestFactory`), so they are owned by that workspace. A Studios build is private only when the workspace sets its own container repository. Otherwise it goes to the community repository, through `tower.data-studio.wave.custom-image-registry` or Wave's default for freeze builds, and stays public by the rule above
- Visibility is set when the record is created and never changes. Making a private build public means building it again as public
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
no token (Authorization header or Wave session, 3)   → 401
caller = userInfo(record.owner.towerEndpoint, token)
    invalid                                          → 401
record.owner.workspaceId == null
    caller.id == record.owner.userId                 → allow
record.owner.workspaceId in workspaces(caller)       → allow
caller is 'owner' of org record.owner.orgId          → allow
otherwise                                            → 403

any Platform call above: unpaired, timeout or 5xx,
and no cached result                                 → 503
```

- The Platform asked is always the one stored on the record. Clients send no endpoint on the read path, so a request can't point Wave at another host
- `workspaces(caller)` comes from Platform's `GET /user/{userId}/workspaces` on the record's endpoint, called with the caller's own token. It returns the caller's workspaces with their `orgId`, and Platform allows a caller to list only their own. This needs a new `TowerClient` method and response model
- That listing has one row per role the caller holds (`WorkspaceDao.findAllByParticipantUserId`): a workspace participant role gives a row with the `workspaceId`, an organisation role gives a row with the `orgId`, a null `workspaceId` and the org `roles`. An organisation owner who isn't also a participant of a workspace gets no row for it, yet administers it in Platform. The org-owner rule covers them from the same response, with no extra Platform call. Organisation `member` and `collaborator` roles alone don't grant access, as in Platform
- The token is validated exactly as presented. The submit path's `TowerConnector.sendAsync1` swaps in a newer token from `JwtAuthStore` and refreshes on a 401. The access path calls Platform without the store lookup and without refresh: a 401 from Platform is a 401 from Wave
- The user-info and workspace results are each cached for 60 seconds, under keys separate from the submit path's `userInfo` cache entries
- When the record's Platform is unpaired or doesn't answer (timeout or 5xx) and no cached result is left, the check fails closed with 503. Wave never serves an expired user-info or workspace result. Public records are unaffected
- A record's logs, conda lock file, SPDX file and scan logs follow the record

**Rationale:**
- Refreshing would let an expired or leaked token keep working for up to the 30 days `JwtAuthStore` keeps it
- 60 seconds is the delay before a revoked token or a removed membership takes effect
- Serving an expired result while Platform is down would stretch that delay to the length of the outage. Only the record's Platform can decide access, so while it is unavailable its private records are unavailable too

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
  orgId           // set on private records with a workspace
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
- `orgId` is required on private records with a workspace: the access check lets the organisation owner in (1.4). The workspace listing called for private requests (1.5) already returns it, so it comes at no extra cost. Public records may leave it null

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

**Cost:** content built privately is built and scanned once per workspace that requests it, with a cache scoped to that workspace. The build cache is keyed by container id (`ref=${cacheRepository}:${containerId}` in `BuildStrategy`), so a private build shares no cache with other workspaces or with public builds: the first private build of some content in a workspace is a full build, later ones in the same workspace hit its cache. This is intended: a private build shares nothing outside its workspace, its cache included.

#### 2.3 One canonical endpoint string

**Decision:** Normalise the endpoint once at submit, after `patchPlatformEndpoint`: lowercase the scheme and host, drop the default port and the trailing slash. Use the same form as the key the pairing lookup accepts.

**Rationale:** the endpoint is now part of the owner. It decides the build id, the owner and the Platform a token is checked against. Two spellings of the same Platform would otherwise become two owners.

### 3. View Pages

**Decision:**

| Page | Behaviour |
|---|---|
| `GET /view/builds/{id}`, `/view/containers/{token}`, `/view/mirrors/{id}`, `/view/scans/{id}` | Public record: renders as today. Private record: access check (1.4) using the viewer's Wave session for the record's Platform; with no session, sign-in through that Platform |
| Build list and scan list pages (`allBuilds`, `allScans`) | Private entries the viewer can't read are left out of the list. Lists never start a sign-in: they use a session only if the viewer already has one |
| `POST /view/scans`, `GET /view/inspect` | Unchanged. They create public records or show public images |

**Wave signs the viewer in through the record's Platform with OpenID Connect.** Platform is an OpenID Connect provider (`platform-oidc`: `/.well-known/openid-configuration`, `/authorize`, `/token`, `/.well-known/jwks.json`), so this needs no new Platform endpoint:

1. A private record and no Wave session for `record.owner.towerEndpoint`: Wave redirects the browser to that Platform's `/authorize`, authorization code flow with PKCE. The `state` holds the page to return to; the PKCE verifier stays in Wave's store, keyed by `state`, for a few minutes
2. A user already signed in to Platform is sent straight back to Wave's callback (`/view/auth/callback`) with a code. A user who isn't signs in to Platform first
3. Wave exchanges the code at the Platform's `/token` and stores the token in a Wave session: a host-only, `HttpOnly`, `Secure`, `SameSite=Lax` cookie referencing the token server side, one token per Platform endpoint
4. The access check (1.4) runs with that token, exactly as the API path checks a bearer token: `userInfo` on the record's Platform, then the workspace listing. The token is never refreshed (1.4): when it expires, the next private page signs in again, which is a redirect with no prompt while the Platform session lasts

- **Sign-in fails or is refused: 401.** The page says "This build belongs to a Seqera Platform workspace. Sign in to Seqera Platform to view it", with a link that restarts the sign-in
- **Signed in, not a member: 403.** The page says the record belongs to a workspace the viewer doesn't have access to
- **Platform without OpenID Connect** (no `TOWER_OIDC_PEM_PATH`): no sign-in is possible, private records show the 401 page with a link to the Platform, and the API (4.2) stays the way to read them
- **A counter for refused views**, by endpoint and status, so a client sending `private` too widely shows up in monitoring

**Rationale:**
- Links Wave and Platform have already handed out (`detailsUri`, build notification emails, `ChildRefs`, Platform's build, scan and mirror URLs, the Data Studios build link) keep pointing at `/view/…`. Public records open as today; private records open for a member of the record's workspace, after a redirect through that Platform
- A browser following a link sends no `Authorization` header, so the page needs a sign-in of its own. Sharing Platform's `JWT` cookie with sibling subdomains (`TOWER_AUTH_COOKIE_DOMAIN`) was considered and rejected: Platform has deprecated it (PLAT-3288), it hands the full Platform session to every service under the parent domain, and it can't work for a Platform on another domain
- Asking the viewer to paste a Platform access token was rejected: those tokens are long-lived and carry the user's full API access, and pasting them into another site is a phishing pattern
- One access check backs the pages and the API: only how the token is obtained differs

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

Once the new API ships, they are marked deprecated with a `Deprecation` header, and removed on an announced date. That date comes only after Platform, Nextflow and the Wave CLI read through the new API: Platform's own `WaveClient` polls `/v1alpha1/builds/{id}/status` and `/logs` with no token today, and would stop seeing its private Data Studios builds complete.

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
| 5 | View pages | OpenID Connect sign-in against the record's Platform: discovery, authorization code with PKCE, callback, Wave session cookie (3). Access check on the record pages, filtering on the list pages, 401 and 403 templates, refused-view counter (3) | L |
| 6 | New read API | New API version with optional bearer auth (4.2). `Deprecation` header on the current API (4.1) | M |

Outside Wave:

| Component | Change | When |
|---|---|---|
| Platform | Register Wave as an OpenID Connect client with its callback URL, enable OpenID Connect (`TOWER_OIDC_PEM_PATH`) in each environment, and confirm its access tokens are accepted by `user-info` and `GET /user/{userId}/workspaces` | Before PR 5 ships |
| Platform | Read build status and logs (`WaveClient.getBuildStatus`, `getLogs`, used by Data Studios' `CustomContainerWorker`) through the new read API, with a fresh access token minted for the studio's user on each read, as Platform already does to submit (`CustomContainerRequestSubmittor.getTowerAccessToken`), so token expiry during a long build doesn't matter. Point the Studios "Full log available" link at `/view/builds/{id}` instead of the raw `/v1alpha1/builds/{id}/logs` URL, which a browser can't authenticate to | After PR 6, before Platform sends `private` |
| Platform | Send `visibility: private` for Data Studios builds (1.2) | After PR 5 is live |
| Nextflow | Expose `wave.visibility`; read through the new API with the token | Any time after PRs 2 and 6 |
| Wave CLI | Expose `--visibility`; read through the new API with the token | Any time after PRs 2 and 6 |

Rollout: PRs 1 to 6 change nothing visible, because no record is private yet; test on staging with a private request sent by hand. The first visible change is Platform sending `private`, a Platform-side switch that can be turned off without a Wave release. Removing the current read API is a later, announced step.

### Tests

- An anonymous request with no endpoint stores no `owner` and a public record, even though `towerEndpoint` defaults to Seqera Cloud
- `private` on an anonymous request, or on the community repository, gives a public record and an unchanged build id
- The same Containerfile sent as private from two workspaces, or from the same workspace id on two endpoints, gives two build ids; from one workspace, or sent as public, it gives one
- A private request naming a workspace the caller doesn't belong to is rejected with 403
- Access check: a token from another endpoint gets 401; an expired token gets 401 and isn't refreshed; a member of the record's workspace passes; an owner of the record's organisation who isn't a workspace participant passes; an organisation member who isn't a workspace participant gets 403; a valid user outside the workspace gets 403; on a personal-workspace record only the same user passes; results are cached for 60 seconds; with the record's Platform unpaired, timing out or returning 5xx, a cached result is still used and an expired one gives 503
- View pages: a private record with no session redirects to the record's Platform `/authorize`, never to another endpoint; a callback with an unknown or expired `state`, or a failed PKCE exchange, gives 401; the session cookie is host-only and `HttpOnly`; list pages never redirect
- The current API still returns private records; the new API applies the check
- Records written before this change still load and are public

## Consequences

- **The current API returns private records** to anyone holding the id until it is removed. Private records are protected on every path only after that, so the deprecation period should be short and announced with a date
- **Privacy depends on clients opting in.** A client that should send `private` and doesn't creates a public record
- **A sign-in redirect on the first private page.** A viewer with no Wave session for the record's Platform is redirected through it once per session; with an active Platform session there is no prompt
- **Platforms without OpenID Connect** can't sign viewers in, so their private records are readable only through the API (4.2)
- **One more Platform call per viewer.** Reading a private record costs a user-info call and a workspace listing on the record's Platform, each cached for 60 seconds. Private submits with a workspace pay the workspace listing too
- **More builds, scans and cache storage.** Once Platform sends `private`, content that today is built and scanned once is built, scanned and cached once per workspace that requests it (2.2)
- **Scans of existing images** are keyed by image and digest, so they are shared by everyone who scans the same image. They contain only vulnerability data. Adding the owner to that scan id is a small follow-up
- **Existing records stay public** for as long as they are kept

## Open Questions

1. How does Wave register as a client of an enterprise Platform: a static client configured per Platform, or dynamic registration (`/register`)? And which scopes does Wave request so its token is accepted by `user-info` and the workspace listing?
2. The version name of the new read API, and the deprecation date of the current one
3. Should the new API's `/status` endpoints also require a token for private records, or stay open since they expose only build state?
4. Which repository does Seqera Cloud use for Data Studios builds (`tower.data-studio.wave.custom-image-registry`)? If it is the community registry, as on enterprise stage, only workspaces with their own container repository get private Studios builds (1.2)
