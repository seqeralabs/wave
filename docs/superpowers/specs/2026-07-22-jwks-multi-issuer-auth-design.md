# JWKS-based multi-issuer authentication for Wave

**Date:** 2026-07-22
**Status:** Approved design (rev 2 — builds on Micronaut Security JWKS support and existing Platform OIDC module)
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
5. **Reuse Micronaut Security's JWKS machinery** rather than re-implementing it: the library
   builds on `micronaut-security-jwt`'s `JwksClient` (pluggable transport), `JwkSetFetcher`
   (fetch + caching) and `JwkValidator` (signature verification) extension points, adding only
   the dynamic multi-issuer resolution Micronaut does not provide (its `SignatureConfiguration`
   beans are fixed at startup via static config).

## Constraints discovered during investigation

- **Platform user-session JWTs are signed with symmetric HS256**
  (`micronaut.security.token.jwt.signatures.secret.generator`). A JWKS cannot publish a
  symmetric key, so these tokens are never JWKS-verifiable.
- **Platform personal access tokens (PATs) are opaque** — random secrets checked against the
  Platform DB (`AccessTokenServiceImpl`), not JWTs. They can never be verified via JWKS.
  The existing delegation path is retained for them.
- **Platform already ships most of the issuer-side machinery** in the `platform-oidc` module,
  active when `TOWER_OIDC_PEM_PATH` (RSA keypair PEM) is configured:
  - `GET /.well-known/jwks.json` (`OpenIdConnectController`) serves the RSA public key set
    with a stable RFC 7638 thumbprint `kid`, `use: sig`, `alg: RS256`;
  - `TokenIssuer` mints RS256 access tokens with `kid` header and `iss` claim set to the
    Platform API endpoint URL (`TowerService.getTowerPublicEndpoint()`);
  - `OidcRSASignatureConfiguration` makes Platform accept RS256 tokens as inbound
    authentication alongside HS256;
  - token-exchange plumbing exists (`GET /exchange/token` + `TokenIssuer.issueRefreshableAccessToken`)
    but is currently Studio-specific (session-initialization tokens).
- The pairing tunnel already carries arbitrary HTTP GETs (`ProxyHttpRequest`/`ProxyHttpResponse`
  correlated by `msgId`, routed by `tower:<host>` Redis queues). Fetching a JWKS document over it
  requires **no changes to lib-pairing**.
- Wave's JWT store/refresh machinery (`JwtAuthStore`, `JwtMonitor`, `/oauth/access_token` refresh)
  exists so Wave can call Platform APIs *on behalf of the user* during long pipelines.
  That is a delegated-credential concern, orthogonal to authentication. **It is not touched.**
- Micronaut Security (v4.11) extension points verified in source:
  - `JwksClient` — `@FunctionalInterface`, `Publisher<String> load(String providerName, String url)`;
    default `HttpClientJwksClient` is disabled via
    `micronaut.security.token.jwt.signatures.jwks-client.http-client.enabled: false`, letting a
    custom bean take over all JWKS retrieval;
  - `JwkSetFetcher<JWKSet>` — `fetch(providerName, url)` + `clearCache(url)`; the default
    (`DefaultJwkSetFetcher` behind a caching `@Primary` wrapper) parses and caches the key set
    per URL (default expiration 60s; a Micronaut-cache-backed variant `CacheableJwkSetFetcher`
    activates when a `jwks` cache is configured — that is the hook for distributed caching);
  - `JwkValidator`/`DefaultJwkValidator` — verifies a `SignedJWT` against a `JWK`
    (RSA/EC only — symmetric keys can never pass).

## Platform prerequisites (out of scope here, tracked separately)

Mostly satisfied by the existing `platform-oidc` module:

- **P1 (done)** — JWKS endpoint: `/.well-known/jwks.json` with proper `kid`/`use`/`alg`.
- **P2 (done)** — `iss` claim = Platform API endpoint URL, `kid` in header, for tokens minted
  by `TokenIssuer`; Platform accepts these RS256 tokens as inbound auth.
- **P3 (remaining)** — get an RS256 token into the Wave request flow: the tokens clients send
  today (HS256 session JWTs, opaque PATs) are not JWKS-verifiable. Options: a generic
  token-exchange grant on the existing `TokenIssuer` machinery (PAT/session JWT in → RS256
  access token out), or issuing RS256 tokens at workflow-launch time.
- **P4 (deployment)** — `TOWER_OIDC_PEM_PATH` must be configured (it is optional today;
  no PEM → no JWKS endpoint → Wave's dual path keeps everything on the legacy route).

## Trust model

Pairing **is** issuer registration. A JWT is accepted only when:

1. `request.towerEndpoint` has a live `PairingRecord` (unchanged from today — this is the
   existing trust anchor).
2. The JWKS for that endpoint verifies the token signature.
3. The token's `iss` claim equals the normalized `towerEndpoint`
   (same normalization Wave already applies: scheme/trailing-slash/legacy-host patching).
4. `exp` is valid (with small configurable clock skew) and the algorithm is on the allow-list
   (RS256/RS384/RS512/ES256/ES384/ES512 — `none` and HS* are never accepted on this path).

### Dual path — routing by token shape

The token's own header decides the path; no capability probing of the Platform version:

| Token | Path |
|---|---|
| Signed JWT, asymmetric alg (RS/ES) | JWKS validation (strict — failure rejects the request, no fallback) |
| Signed JWT, HS256 (session tokens) | Legacy delegation (`/user-info`) |
| Opaque PAT | Legacy delegation (`/user-info`) |

There is no downgrade attack: forcing the legacy path just means full delegation validation
by the Platform itself. The asymmetric-JWT path never silently falls back — a JWKS-verifiable
token that fails verification is rejected.

## Architecture

### New libseqera module: `lib-auth-jwks` (package `io.seqera.auth.jwks`)

Wave-agnostic Micronaut library (same style as `lib-pairing`). Dependencies:
`io.micronaut.security:micronaut-security-jwt` (api). No dependency on Wave, lib-pairing, or Redis.

- **`JwtInspector`** — static helpers: is this string a signed JWT, and is its alg asymmetric?
  Drives the dual-path branch without cryptographic work.
- **`JwksVerifierConfig`** (interface) — `getJwksPath()`, `getRefreshMinInterval()`,
  `getClockSkew()`, `getFetchTimeout()`. Implemented by the consuming service
  (same pattern as lib-pairing's `PairingConfig`).
- **`DynamicJwksVerifier`** (`@Singleton`, requires a `JwksVerifierConfig` bean) — the entry point:
  `VerifiedJwt verify(String token, String expectedIssuer)`. Composes Micronaut beans
  (`JwkSetFetcher<JWKSet>`, `JwkValidator`) instead of re-implementing them:
  - alg allow-list check on the parsed header;
  - resolve the JWKS URL dynamically: `normalizeIssuer(endpoint) + jwksPath`, fetch via
    `jwkSetFetcher.fetch(endpoint, url)` (`providerName` = the endpoint, so a custom
    `JwksClient` can route by issuer) — caching comes from Micronaut's fetcher;
  - unknown `kid` → rate-limited `clearCache(url)` + re-fetch (key rotation), min interval
    `refreshMinInterval` per endpoint;
  - signature check: Nimbus `JWKSelector`/`JWKMatcher.forJWSHeader` key selection +
    `jwkValidator.validate(jwt, jwk)`;
  - claims: `exp`/`nbf` via Nimbus claims verifier with configurable skew; `iss` compared
    against the normalized expected issuer;
  - typed failures: `InvalidTokenException`, `UnknownIssuerException`, `JwksUnavailableException`.
- **`VerifiedJwt`** — immutable claims view (issuer, subject, expiration, raw claim map).

What the library deliberately does NOT contain: HTTP fetching (Micronaut's `HttpClientJwksClient`
is the default transport), JWKS parsing/caching (Micronaut's `JwkSetFetcher`), signature
primitives (Micronaut's `JwkValidator`). A consumer with a plain network path needs zero
custom transport code.

### Wave-side integration (thin adapters)

- **`PairingJwksClient implements JwksClient`** — routes `load(providerName, url)` through the
  existing `TowerConnector.sendAsync(endpoint, ProxyHttpRequest)` with a GET for the JWKS URL and
  no auth header (JWKS is public); `providerName` carries the Tower endpoint for tunnel routing.
  Because `TowerConnector` is the abstraction, this transparently uses the websocket tunnel
  (default) or direct HTTP (`legacy-http-connector` env) — private-network support comes for free.
  Micronaut's own HTTP JWKS client is disabled via
  `micronaut.security.token.jwt.signatures.jwks-client.http-client.enabled: false`, making the
  pairing client the sole `JwksClient` bean.
- **Caching** — Micronaut's default in-memory `JwkSetFetcher` cache (per replica, 60s). Good
  enough: a JWKS fetch is one small GET per issuer per minute worst case. If distributed caching
  is ever wanted, configure a Micronaut cache named `jwks` (libseqera's `micronaut-cache-redis`
  module) and `CacheableJwkSetFetcher` activates without code changes. There is no custom cache
  layer and no stale-cache fallback: if the JWKS cannot be fetched and the cache has expired,
  asymmetric-JWT requests are rejected until the tunnel recovers (PAT/HS256 traffic is unaffected).
- **Service wiring** — in `UserServiceImpl.getUserByAccessToken`, the single choke point
  both `ContainerController` and `InspectController` resolve identity through (and which
  they only reach after the pairing-record check):
  - if `wave.auth.jwks.enabled` and `JwtInspector` says asymmetric JWT →
    `dynamicJwksVerifier.verify(token, endpoint)`; on success proceed, on failure → 401.
  - Phase 1 keeps the `/user-info` call after successful verification, because `PlatformId`
    needs the `User` entity (id, email) and current Platform claims don't carry it.
    Phase 2 (after Platform enriches claims) builds `PlatformId` from claims and drops the
    `/user-info` call for verified JWTs.
  - otherwise → existing path unchanged.
- **Config** (`wave.auth.jwks.*`): `enabled` (default `false`), `path`
  (default `/.well-known/jwks.json` — matches Platform's existing endpoint),
  `refresh-min-interval` (rate limit for kid-miss refetch, default 60s), `clock-skew` (60s),
  `fetch-timeout` (30s). JWKS cache expiration is Micronaut's (60s default).

### What is intentionally NOT changed

- `JwtAuthStore` / `JwtTimeStore` / `JwtMonitor` / token refresh — untouched (delegated
  credential lifecycle, still required for credentials fetch and workflow describe).
- lib-pairing protocol and message types — untouched.
- Client-facing API — token stays in the request body. Moving to `Authorization: Bearer`
  with Micronaut's `SecurityFilter` is a possible later phase, not in scope.
- No generic token exchange yet — the plumbing exists on Platform (`TokenIssuer`), but wiring
  a PAT→RS256 grant is Platform work (prerequisite P3), not part of this change.

## Error handling summary

| Condition | Behavior |
|---|---|
| Asymmetric JWT, bad signature / expired / iss mismatch | 401, request rejected |
| Asymmetric JWT, unknown `kid` | rate-limited JWKS refetch (cache cleared), then verify or 401 |
| JWKS unreachable, Micronaut cache still valid | verify from cache |
| JWKS unreachable, cache expired or empty | 401 with distinct log/metric (`JwksUnavailableException`) |
| No pairing record for endpoint | 400 (unchanged, existing behavior) |
| HS256 JWT or opaque PAT | legacy `/user-info` delegation (unchanged) |

## Testing

- **lib-auth-jwks** (Spock): generated RSA/EC keypairs; stubbed `JwkSetFetcher`; verify happy
  path, tampered signature, expired token, wrong issuer, alg confusion (`none`, HS256),
  kid rotation triggering `clearCache` + refetch, refetch rate limiting, fetch-failure → 
  `JwksUnavailableException`, issuer normalization.
- **Wave** (Spock): dual-path branching (asymmetric JWT vs HS256 vs opaque), flag off/on,
  `PairingJwksClient` against a stubbed `TowerConnector`, `UserServiceImpl` 401 behavior.
- **Regression**: adding `micronaut-security-jwt` to Wave's classpath activates Micronaut's JWT
  token readers in the security filter — verify the basic-auth admin endpoints and anonymous
  routes behave exactly as before.
- **Integration**: verified in staging against a Platform instance with `TOWER_OIDC_PEM_PATH` set.

## Rollout

1. Ship with `wave.auth.jwks.enabled=false` — zero behavior change.
2. Enable in staging against a Platform with `TOWER_OIDC_PEM_PATH` configured and P3 in place.
3. Enable in production; HS256/PAT traffic continues on the legacy path throughout, so older
   Platform instances are unaffected indefinitely.
