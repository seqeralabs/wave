# JWKS-based multi-issuer authentication for Wave

**Date:** 2026-07-22
**Status:** Approved design
**Scope:** Wave + new libseqera module `lib-auth-jwks` (Platform-side changes listed as prerequisites only)

## Problem

Wave performs no cryptographic validation of the Platform (Tower) JWTs it receives.
Authentication is delegation-based: the token from `SubmitContainerTokenRequest.towerAccessToken`
is forwarded to `<towerEndpoint>/user-info` (over the pairing websocket tunnel or direct HTTP)
and Wave trusts the response. Goals:

1. Validate JWTs locally using the canonical JWKS protocol (signature, expiry, issuer).
2. Support **multiple Platform issuers** dynamically — any Platform instance that pairs with
   Wave becomes a trusted issuer, with no static per-issuer configuration.
3. Support issuers **behind private networks** — the JWKS document must be retrievable over
   the existing pairing websocket channel, since Wave may have no direct route to the Platform.
4. Keep the implementation Wave-agnostic in a reusable library (`lib-auth-jwks` in libseqera,
   package `io.seqera.auth.jwks`) so other services can adopt it.

## Constraints discovered during investigation

- **Platform signs JWTs with symmetric HS256** (`micronaut.security.token.jwt.signatures.secret.generator`).
  A JWKS cannot publish a symmetric key. Platform must add asymmetric signing (prerequisite P1).
- **Platform personal access tokens (PATs) are opaque** — random secrets checked against the
  Platform DB (`AccessTokenServiceImpl`), not JWTs. They can never be verified via JWKS.
  The existing delegation path is retained for them (decision: cheapest option, no token exchange).
- The pairing tunnel already carries arbitrary HTTP GETs (`ProxyHttpRequest`/`ProxyHttpResponse`
  correlated by `msgId`, routed by `tower:<host>` Redis queues). Fetching a JWKS document over it
  requires **no changes to lib-pairing**.
- Wave's JWT store/refresh machinery (`JwtAuthStore`, `JwtMonitor`, `/oauth/access_token` refresh)
  exists so Wave can call Platform APIs *on behalf of the user* during long pipelines.
  That is a delegated-credential concern, orthogonal to authentication. **It is not touched.**

## Platform prerequisites (out of scope here, tracked separately)

- **P1** — Sign access JWTs with RS256 or ES256, including a `kid` header. Keep HS256
  verification during the transition (Micronaut supports multiple signature configurations).
- **P2** — Expose a JWKS endpoint serving the public key set. Path served under the Platform
  API; Wave treats the path as configurable.
- **P3** — Set the `iss` claim to the Platform/custom API endpoint URL (the same URL the
  pairing is registered under, post-normalization).

## Trust model

Pairing **is** issuer registration. A JWT is accepted only when:

1. `request.towerEndpoint` has a live `PairingRecord` (unchanged from today — this is the
   existing trust anchor).
2. The JWKS for that endpoint verifies the token signature.
3. The token's `iss` claim equals the normalized `towerEndpoint`
   (same normalization Wave already applies: scheme/trailing-slash/legacy-host patching).
4. `exp` is valid (with small configurable clock skew) and the algorithm is on the allow-list
   (RS256/ES256 — `none` and HS* are never accepted on this path).

### Dual path — routing by token shape

The token's own header decides the path; no capability probing of the Platform version:

| Token | Path |
|---|---|
| Signed JWT, asymmetric alg (RS/ES) | JWKS validation (strict — failure rejects the request, no fallback) |
| Signed JWT, HS256 (older Platform) | Legacy delegation (`/user-info`) |
| Opaque PAT | Legacy delegation (`/user-info`) |

There is no downgrade attack: forcing the legacy path just means full delegation validation
by the Platform itself. The asymmetric-JWT path never silently falls back — a JWKS-verifiable
token that fails verification is rejected.

## Architecture

### New libseqera module: `lib-auth-jwks` (package `io.seqera.auth.jwks`)

Wave-agnostic. Dependencies: `nimbus-jose-jwt` (+ optionally `micronaut-inject` annotations,
matching lib-pairing's style). No dependency on Wave, lib-pairing, or Redis.

- **`JwksFetcher`** (interface) — `CompletableFuture<String> fetch(String issuerEndpoint)`
  returns the raw JWKS JSON for an issuer. This is the transport seam: the library ships a
  default `HttpJwksFetcher` (java.net.http); Wave plugs in a pairing-tunnel implementation.
  The JWKS path relative to the endpoint is configuration (default `/.well-known/jwks.json`).
- **`JwksCacheStore`** (interface) — get/put of the cached JWKS document per issuer with TTL.
  Default in-memory (Caffeine) implementation in the library; consumers may provide a
  distributed one.
- **`JwksJwtVerifier`** — the entry point:
  `VerifiedJwt verify(String token, String expectedIssuer)`.
  Internally a Nimbus `ConfigurableJWTProcessor` with a custom `JWKSource` backed by
  `JwksCacheStore` + `JwksFetcher`. Behavior:
  - cache hit → verify locally, zero network I/O;
  - unknown `kid` → rate-limited re-fetch (key rotation), then verify;
  - fetch failure with a stale cached document → use the stale copy (verification is still
    cryptographic; staleness only delays rotation pickup);
  - fetch failure with no cache → typed failure (`JwksUnavailableException`).
  Verifies signature, `exp`/`nbf` (configurable skew), `iss` equality, alg allow-list.
  Typed exceptions: `InvalidTokenException`, `UnknownIssuerException`, `JwksUnavailableException`.
- **`JwtInspector`** — static helper: is this string a signed JWT, and is its alg asymmetric?
  Drives the dual-path branch without cryptographic work.
- **`VerifiedJwt`** — immutable claims view (subject, issuer, expiry, raw claim map).

### Wave-side integration (thin adapters)

- **`PairingJwksFetcher implements JwksFetcher`** — delegates to the existing
  `TowerConnector.sendAsync(endpoint, ProxyHttpRequest)` with a GET for the JWKS path and no
  auth header (JWKS is public). Because `TowerConnector` is the abstraction, this transparently
  uses the websocket tunnel (default) or direct HTTP (`legacy-http-connector` env) — private
  network support comes for free.
- **`RedisJwksCacheStore implements JwksCacheStore`** — backed by
  `io.seqera.data.store.state.AbstractStateStore` (lib-data-store-state-redis), prefix
  `jwks-cache/v1`, TTL `wave.auth.jwks.cache-duration` (default 6h). Shared across replicas.
- **Service wiring** — in `UserServiceImpl.getUserByAccessToken`, the single choke point
  both `ContainerController` and `InspectController` resolve identity through (and which
  they only reach after the pairing-record check):
  - if `wave.auth.jwks.enabled` and `JwtInspector` says asymmetric JWT →
    `jwksJwtVerifier.verify(token, endpoint)`; on success proceed, on failure → 401.
  - Phase 1 keeps the `/user-info` call after successful verification, because `PlatformId`
    needs the `User` entity (id, email) and current Platform claims don't carry it.
    Phase 2 (after Platform enriches claims) builds `PlatformId` from claims and drops the
    `/user-info` call for verified JWTs.
  - otherwise → existing path unchanged.
- **Config** (`wave.auth.jwks.*`): `enabled` (default `false`), `path`
  (default `/.well-known/jwks.json`), `cache-duration` (6h), `refresh-min-interval`
  (rate limit for kid-miss refetch, default 60s), `clock-skew` (60s).

### What is intentionally NOT changed

- `JwtAuthStore` / `JwtTimeStore` / `JwtMonitor` / token refresh — untouched (delegated
  credential lifecycle, still required for credentials fetch and workflow describe).
- lib-pairing protocol and message types — untouched.
- Client-facing API — token stays in the request body. Moving to `Authorization: Bearer`
  with Micronaut's `SecurityFilter` is a possible later phase, not in scope.
- No token exchange for PATs (decision: dual path is permanent until Platform ever offers it).

## Error handling summary

| Condition | Behavior |
|---|---|
| Asymmetric JWT, bad signature / expired / iss mismatch | 401, request rejected |
| Asymmetric JWT, unknown `kid` | rate-limited JWKS refetch, then verify or 401 |
| JWKS unreachable, stale cache present | verify against stale cache |
| JWKS unreachable, no cache | 401 with distinct log/metric (`JwksUnavailableException`) |
| No pairing record for endpoint | 400 (unchanged, existing behavior) |
| HS256 JWT or opaque PAT | legacy `/user-info` delegation (unchanged) |

## Testing

- **lib-auth-jwks** (JUnit/Spock): generated RSA/EC keypairs; verify happy path, tampered
  signature, expired token, wrong issuer, alg confusion (`none`, HS256 signed with a public
  key), kid rotation triggering refetch, rate limiting, stale-cache fallback, no-cache failure.
- **Wave** (Spock): dual-path branching (asymmetric JWT vs HS256 vs opaque), flag off/on,
  `PairingJwksFetcher` against a stubbed `TowerConnector`, controller-level 401 behavior,
  Redis cache store round-trip (existing Redis test fixtures).
- **Integration**: mock Platform serving a JWKS + signed tokens over the pairing test harness.

## Rollout

1. Ship with `wave.auth.jwks.enabled=false` — zero behavior change.
2. Enable in staging against a Platform build with P1–P3.
3. Enable in production; HS256/PAT traffic continues on the legacy path throughout, so older
   Platform instances are unaffected indefinitely.
