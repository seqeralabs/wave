# JWKS Multi-Issuer Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Local JWKS-based validation of Platform JWTs in Wave, multi-issuer, working over the pairing websocket tunnel, with the reusable core in a new libseqera module `lib-auth-jwks` built on Micronaut Security's JWKS machinery.

**Architecture:** `lib-auth-jwks` (Micronaut library, package `io.seqera.auth.jwks`) adds the one thing `micronaut-security-jwt` lacks — dynamic per-issuer JWKS resolution — by composing Micronaut's own beans: `JwkSetFetcher<JWKSet>` (fetch + caching), `JwkValidator` (signature verification), and the `JwksClient` transport seam. Wave plugs the pairing tunnel into that seam (`PairingJwksClient` over `TowerConnector`) and gates everything behind `wave.auth.jwks.enabled` inside `UserServiceImpl.getUserByAccessToken` — the single choke point both `ContainerController` and `InspectController` go through. Asymmetric JWTs get verified locally; HS256 JWTs and opaque PATs continue on today's `/user-info` delegation path untouched.

**Tech Stack:** Java 17 (lib main sources), Groovy/Spock (tests + Wave), `io.micronaut.security:micronaut-security-jwt` 4.11.x (brings Nimbus JOSE), Micronaut 4.x.

**Spec:** `docs/superpowers/specs/2026-07-22-jwks-multi-issuer-auth-design.md` (in the wave repo).

## Global Constraints

- Tasks 1–2 run in `/Users/pditommaso/Projects/libseqera`, tasks 3–5 in `/Users/pditommaso/Projects/wave`. Commit in the repo you changed.
- `lib-auth-jwks` depends ONLY on `io.micronaut.security:micronaut-security-jwt` (api) plus the Micronaut annotation processing the module plugin provides — no Wave, lib-pairing, Redis, or HTTP-client code.
- Lib module: `group 'io.seqera'`, `VERSION` file `0.1.0`, package `io.seqera.auth.jwks`, sourceCompatibility 17 (set by conventions — don't override).
- Copy the Apache-2.0 license header from any existing libseqera source file into every new libseqera file; copy the Wave (AGPL) header from any existing Wave source file into every new Wave file. Not repeated in the code blocks below.
- Allowed JWT algorithms everywhere: RS256/RS384/RS512/ES256/ES384/ES512. Never `none`, never HS*.
- Wave feature flag `wave.auth.jwks.enabled` defaults to `false` — merged code must be a no-op until enabled.
- Config defaults (from spec): path `/.well-known/jwks.json`, refresh-min-interval `60s`, clock-skew `60s`, fetch-timeout `30s`. JWKS caching is Micronaut's `JwkSetFetcher` (in-memory, 60s) — no custom cache layer.
- Wave tests: `./gradlew test --tests '<ClassName>'`. Lib tests: `./gradlew :lib-auth-jwks:test`.

---

## Backward compatibility: existing token propagation & validation model

**Requirement:** JWKS validation must not alter Wave's existing custom token handling — the
delegated-credential model centred on `TowerConnector` (`JwtAuth`, `JwtAuthStore`, `JwtMonitor`,
the 401→refresh cycle) — for any traffic that flows through it today. Older Platform instances and
every non-asymmetric token must behave exactly as before.

### What that model does today

Two distinct concerns share the same `towerAccessToken`:

1. **Authentication** (identity resolution) — `UserServiceImpl.getUserByAccessToken` forwards the
   token to `/user-info` and trusts the answer. This is the *validation model* JWKS replaces, and
   only for asymmetric JWTs.
2. **Delegated propagation** — after authentication, Wave calls Platform APIs *on behalf of the
   user* for the whole pipeline lifetime (credentials, workflow describe). `TowerConnector`
   attaches `Authorization: Bearer <bearer>` (`sendAsync1`, `TowerConnector.groovy:216`) and, on a
   401, POSTs `grant_type=refresh_token` to `/oauth/access_token`, parses the `JWT`/`JWT_REFRESH_TOKEN`
   cookies, and updates `JwtAuthStore` (30-day TTL, kept warm by `JwtMonitor`). This is *credential
   lifecycle*, not authentication.

JWKS touches only concern (1). The spec already scopes concern (2) as "not touched"; this section
states *why* that holds and *where* the two could still collide.

### Why the propagation model is unaffected (by construction)

- **JWKS fetch bypasses the auth/refresh path.** `PairingJwksClient` calls the low-level abstract
  `TowerConnector.sendAsync(endpoint, ProxyHttpRequest)` (raw proxy send), **not** `sendAsync1`. It
  never reads `JwtAuthStore`, never attaches a Bearer, never triggers a refresh — the JWKS document
  is public. Adding JWKS retrieval therefore cannot perturb the token store or the refresh cache.
- **Refreshed tokens are HS256 and are never JWKS-validated.** The token minted by
  `/oauth/access_token` (the `JWT` cookie) is a Platform HS256 session token. Even when the *inbound*
  token is RS256, every *refreshed* token that subsequently flows through `sendAsync1` is HS256. This
  is fine: JWKS validation runs exactly once, on the inbound `auth.bearer` at identity-resolution
  time — never on tokens in flight through `TowerConnector`. The refresh cycle keeps producing HS256
  tokens and keeps working untouched.
- **Keying and storage are unchanged.** `JwtAuth.key = md5(endpoint:token)` and `JwtAuthStore` are
  not modified; JWKS reads `auth.bearer` before the existing `userInfo` call and adds no store entries.
- **Dual-path routing preserves every legacy token.** HS256 session JWTs and opaque PATs —
  everything Wave receives today — route to the unchanged `/user-info` path because
  `JwtInspector.isAsymmetricJwt` returns false. With `wave.auth.jwks.enabled=false` (default) even
  RS256 tokens take the legacy path. No currently-deployed token/endpoint combination changes behavior.

### Residual risks — things to verify, not assume

1. **`micronaut-security-jwt` on the classpath changes Wave's *own* request validation.** Wave does
   not use Micronaut Security for the tower token (it lives in the request body, handled manually).
   Adding the dependency activates Micronaut's JWT token readers/validators in the security filter,
   which could start acting on the `Authorization` header of Wave's own endpoints or shift
   basic-auth/anonymous behavior. This is the one real backward-compat hazard and it lives *outside*
   `TowerConnector`. Task 5's regression step must confirm the admin basic-auth and anonymous routes
   are unchanged; disable the unused bearer reader via config
   (`micronaut.security.token.jwt.bearer.enabled: false`) if anything regresses — never via code.
2. **Phase 2 removes the implicit "is this token usable for delegation?" check.** In phase 1 the
   `/user-info` call still runs after JWKS success, so delegation-capability stays implicitly proven.
   When phase 2 drops `/user-info` for verified JWTs, a token can pass JWKS validation yet be unusable
   for on-behalf-of API calls (e.g. an RS256 launch token with narrower scope than a session JWT).
   The propagation code is unchanged, but the *guarantee* that a JWKS-verified identity carries
   working delegated credentials is weakened. Phase 2 must re-establish that guarantee before
   dropping the call — it is not a free simplification.
3. **RS256 inbound token + existing refresh semantics.** An RS256 access token obtained via
   token-exchange (prerequisite P3) is typically short-lived. If it expires mid-pipeline,
   `sendAsync1`'s 401→refresh path handles it exactly as today (yielding an HS256 cookie token). No
   code change needed, but its *lifetime* characteristics differ from today's session JWTs — exercise
   this in the staging integration step.

### Net assessment

The requirement is satisfied by design: JWKS is purely **additive and read-only** with respect to
the propagation/refresh machinery in `TowerConnector`. The only genuine backward-compat surface is
the `micronaut-security-jwt` dependency's effect on Wave's own security filter (risk 1) — already
noted in Task 5, reiterated here as the item that must be actively verified rather than assumed.

---

### Task 1: lib-auth-jwks module scaffold + JwtInspector

**Files:**
- Modify: `/Users/pditommaso/Projects/libseqera/settings.gradle` (add include after `include('lib-activator')`)
- Create: `lib-auth-jwks/build.gradle`
- Create: `lib-auth-jwks/VERSION`
- Create: `lib-auth-jwks/README.md`
- Create: `lib-auth-jwks/src/main/java/io/seqera/auth/jwks/JwtInspector.java`
- Test: `lib-auth-jwks/src/test/groovy/io/seqera/auth/jwks/JwtInspectorTest.groovy`

**Interfaces:**
- Produces: `JwtInspector.isSignedJwt(String): boolean`, `JwtInspector.isAsymmetricJwt(String): boolean` (both static, null-safe → false).

- [ ] **Step 1: Register the module and create the build files**

`settings.gradle` — add after `include('lib-activator')`:

```gradle
include('lib-auth-jwks')
```

`lib-auth-jwks/VERSION`:

```
0.1.0
```

`lib-auth-jwks/build.gradle` (same shape as `lib-pairing/build.gradle`, minus the pieces this lib doesn't need):

```gradle
plugins {
    id 'io.seqera.java-library-conventions'
    id 'io.seqera.groovy-library-conventions'
    // Micronaut minimal lib
    // https://micronaut-projects.github.io/micronaut-gradle-plugin/latest/
    id "io.micronaut.minimal.library" version '4.5.3'
}

group = 'io.seqera'
version = "${project.file('VERSION').text.trim()}"

dependencies {
    // Micronaut Security JWKS machinery (brings nimbus-jose-jwt transitively)
    api "io.micronaut.security:micronaut-security-jwt:4.11.2"

    // Micronaut
    compileOnly "io.micronaut:micronaut-inject-groovy:${micronautCoreVersion}"

    // Testing
    testImplementation "io.micronaut:micronaut-inject-groovy:${micronautCoreVersion}"
    testImplementation "io.micronaut.test:micronaut-test-spock:${micronautTestVersion}"
}

micronaut {
    version '4.8.3'
    processing {
        incremental(true)
    }
    importMicronautPlatform = false
}
```

`lib-auth-jwks/README.md`:

```markdown
# lib-auth-jwks

Dynamic multi-issuer JWKS verification on top of Micronaut Security.

Micronaut's `SignatureConfiguration` beans are fixed at startup via static config; this
library adds per-request issuer resolution for services that accept JWTs from
dynamically-registered issuers. It composes Micronaut's own JWKS machinery —
`JwkSetFetcher` (fetch + caching), `JwkValidator` (signature verification) and the
`JwksClient` transport seam — so a consumer can route JWKS retrieval over any channel
(e.g. a reverse websocket tunnel) by providing a custom `JwksClient` bean.

Entry points: `JwtInspector` (cheap token-shape checks),
`DynamicJwksVerifier.verify(token, expectedIssuer)`.
Consumers must provide a `JwksVerifierConfig` bean.
```

Run: `cd /Users/pditommaso/Projects/libseqera && ./gradlew :lib-auth-jwks:assemble`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Write the failing test**

`src/test/groovy/io/seqera/auth/jwks/JwtInspectorTest.groovy`:

```groovy
package io.seqera.auth.jwks

import java.security.KeyPairGenerator

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import spock.lang.Specification

class JwtInspectorTest extends Specification {

    static String rs256Token() {
        final keyPair = KeyPairGenerator.getInstance('RSA').tap { initialize(2048) }.generateKeyPair()
        final jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID('k1').build(),
                new JWTClaimsSet.Builder().subject('user-1').build() )
        jwt.sign(new RSASSASigner(keyPair.private))
        return jwt.serialize()
    }

    static String hs256Token() {
        final jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder().subject('user-1').build() )
        jwt.sign(new MACSigner(new byte[32]))
        return jwt.serialize()
    }

    def 'should detect signed jwt' () {
        expect:
        JwtInspector.isSignedJwt(rs256Token())
        JwtInspector.isSignedJwt(hs256Token())
        !JwtInspector.isSignedJwt('eyJrandom-opaque-pat')
        !JwtInspector.isSignedJwt('')
        !JwtInspector.isSignedJwt(null)
    }

    def 'should detect asymmetric alg' () {
        expect:
        JwtInspector.isAsymmetricJwt(rs256Token())
        !JwtInspector.isAsymmetricJwt(hs256Token())
        !JwtInspector.isAsymmetricJwt('not-a-jwt')
        !JwtInspector.isAsymmetricJwt(null)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :lib-auth-jwks:test --tests 'JwtInspectorTest'`
Expected: FAIL — `unable to resolve class ... JwtInspector` (compilation error counts as the failing state)

- [ ] **Step 4: Write minimal implementation**

`src/main/java/io/seqera/auth/jwks/JwtInspector.java`:

```java
package io.seqera.auth.jwks;

import java.text.ParseException;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;

/**
 * Cheap, signature-free checks on the shape of a token, used to route
 * between local JWKS verification and legacy delegation paths.
 */
public class JwtInspector {

    public static boolean isSignedJwt(String token) {
        if (token == null || token.isBlank())
            return false;
        try {
            SignedJWT.parse(token);
            return true;
        }
        catch (ParseException e) {
            return false;
        }
    }

    public static boolean isAsymmetricJwt(String token) {
        if (token == null || token.isBlank())
            return false;
        try {
            final JWSAlgorithm alg = SignedJWT.parse(token).getHeader().getAlgorithm();
            return JWSAlgorithm.Family.RSA.contains(alg) || JWSAlgorithm.Family.EC.contains(alg);
        }
        catch (ParseException e) {
            return false;
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :lib-auth-jwks:test --tests 'JwtInspectorTest'`
Expected: PASS

- [ ] **Step 6: Commit (libseqera repo)**

```bash
cd /Users/pditommaso/Projects/libseqera
git add settings.gradle lib-auth-jwks
git commit -m "feat: add lib-auth-jwks module with JwtInspector"
```

---

### Task 2: DynamicJwksVerifier — dynamic multi-issuer layer over Micronaut JWKS beans

**Files:**
- Create: `lib-auth-jwks/src/main/java/io/seqera/auth/jwks/JwksVerifierConfig.java`
- Create: `lib-auth-jwks/src/main/java/io/seqera/auth/jwks/VerifiedJwt.java`
- Create: `lib-auth-jwks/src/main/java/io/seqera/auth/jwks/DynamicJwksVerifier.java`
- Create: `lib-auth-jwks/src/main/java/io/seqera/auth/jwks/exception/InvalidTokenException.java`
- Create: `lib-auth-jwks/src/main/java/io/seqera/auth/jwks/exception/UnknownIssuerException.java`
- Create: `lib-auth-jwks/src/main/java/io/seqera/auth/jwks/exception/JwksUnavailableException.java`
- Create: `lib-auth-jwks/changelog.txt`
- Test: `lib-auth-jwks/src/test/groovy/io/seqera/auth/jwks/DynamicJwksVerifierTest.groovy`

**Interfaces:**
- Consumes (Micronaut Security, verified against v4.11.2 source):
  - `io.micronaut.security.token.jwt.signature.jwks.JwkSetFetcher<T>` — `Publisher<T> fetch(@Nullable String providerName, @Nullable String url)`, `void clearCache(String url)`
  - `io.micronaut.security.token.jwt.signature.jwks.JwkValidator` — `boolean validate(SignedJWT jwt, JWK jwk)` (default impl `DefaultJwkValidator`, RSA/EC only)
- Produces (used by the Wave tasks):
  - `interface JwksVerifierConfig { String getJwksPath(); Duration getRefreshMinInterval(); Duration getClockSkew(); Duration getFetchTimeout(); }`
  - `record VerifiedJwt(String issuer, String subject, Instant expiration, Map<String,Object> claims)`
  - `DynamicJwksVerifier` (`@Singleton`, `@Requires(beans = JwksVerifierConfig.class)`): `VerifiedJwt verify(String token, String expectedIssuer)`; static `String normalizeIssuer(String)`
  - Exceptions: `InvalidTokenException`, `UnknownIssuerException`, `JwksUnavailableException` — all `RuntimeException`, all with `(String)` and `(String, Throwable)` constructors.

**Behavior spec (mirrors the design doc):**
1. Parse token; non-JWT or disallowed alg → `InvalidTokenException`.
2. Fetch the JWKS via `jwkSetFetcher.fetch(normalizedEndpoint, normalizedEndpoint + jwksPath)`, blocking up to `fetchTimeout`. Fetch error or empty key set → `JwksUnavailableException` (Micronaut's fetcher caches internally; no custom cache).
3. Token `kid` absent from the key set → rate-limited `clearCache(url)` + re-fetch (min interval `refreshMinInterval` per endpoint); re-fetch failure keeps the current key set.
4. Key selection via Nimbus `JWKSelector(JWKMatcher.forJWSHeader(...))`, verification via `jwkValidator.validate` — no match or all failures → `InvalidTokenException`.
5. `exp`/`nbf` via Nimbus `DefaultJWTClaimsVerifier` with `clockSkew`; failure → `InvalidTokenException`.
6. Compare `normalizeIssuer(iss claim)` with `normalizeIssuer(expectedIssuer)`; mismatch or missing → `UnknownIssuerException`.

- [ ] **Step 1: Write the failing tests**

`src/test/groovy/io/seqera/auth/jwks/DynamicJwksVerifierTest.groovy`:

```groovy
package io.seqera.auth.jwks

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.micronaut.security.token.jwt.signature.jwks.DefaultJwkValidator
import io.micronaut.security.token.jwt.signature.jwks.JwkSetFetcher
import io.seqera.auth.jwks.exception.InvalidTokenException
import io.seqera.auth.jwks.exception.JwksUnavailableException
import io.seqera.auth.jwks.exception.UnknownIssuerException
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.Shared
import spock.lang.Specification

class DynamicJwksVerifierTest extends Specification {

    static final String ISSUER = 'https://tower.example.com'

    @Shared RSAKey keyA
    @Shared RSAKey keyB
    @Shared JWKSet jwksA
    @Shared JWKSet jwksB

    def setupSpec() {
        keyA = new RSAKeyGenerator(2048).keyID('key-a').generate()
        keyB = new RSAKeyGenerator(2048).keyID('key-b').generate()
        jwksA = new JWKSet(keyA.toPublicJWK())
        jwksB = new JWKSet(keyB.toPublicJWK())
    }

    static class TestConfig implements JwksVerifierConfig {
        String jwksPath = '/.well-known/jwks.json'
        Duration refreshMinInterval = Duration.ofSeconds(60)
        Duration clockSkew = Duration.ofSeconds(60)
        Duration fetchTimeout = Duration.ofSeconds(5)
    }

    static String token(RSAKey key, Map opts = [:]) {
        final claims = new JWTClaimsSet.Builder()
                .issuer((String) opts.getOrDefault('iss', ISSUER))
                .subject('user-123')
                .expirationTime(new Date(System.currentTimeMillis() + (long) opts.getOrDefault('ttlMillis', 60_000L)))
                .build()
        final jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(),
                claims )
        jwt.sign(new RSASSASigner(key))
        return jwt.serialize()
    }

    /**
     * Stub fetcher: returns the i-th key set on the i-th call (last one repeats);
     * a null entry simulates a fetch failure. Tracks fetch and clearCache counts.
     */
    static class StubFetcher implements JwkSetFetcher<JWKSet> {
        final List<JWKSet> responses
        final AtomicInteger fetchCount = new AtomicInteger()
        final AtomicInteger clearCount = new AtomicInteger()

        StubFetcher(List<JWKSet> responses) { this.responses = responses }

        @Override
        Optional<JWKSet> fetch(String url) { return Optional.empty() }

        @Override
        Publisher<JWKSet> fetch(String providerName, String url) {
            final i = Math.min(fetchCount.getAndIncrement(), responses.size() - 1)
            final r = responses[i]
            return r == null ? Mono.error(new RuntimeException('boom')) : Mono.just(r)
        }

        @Override
        void clearCache(String url) { clearCount.incrementAndGet() }
    }

    private DynamicJwksVerifier verifier(StubFetcher fetcher, JwksVerifierConfig config = new TestConfig()) {
        new DynamicJwksVerifier(fetcher, new DefaultJwkValidator(), config)
    }

    def 'should verify a valid token' () {
        when:
        def result = verifier(new StubFetcher([jwksA])).verify(token(keyA), ISSUER)
        then:
        result.issuer() == ISSUER
        result.subject() == 'user-123'
        result.expiration() != null
    }

    def 'should accept issuer with cosmetic differences' () {
        expect:
        verifier(new StubFetcher([jwksA])).verify(token(keyA), 'HTTPS://Tower.Example.Com/').subject() == 'user-123'
    }

    def 'should reject a tampered token' () {
        given:
        def parts = token(keyA).tokenize('.')
        def tampered = parts[0] + '.' + Base64.urlEncoder.withoutPadding().encodeToString('{"sub":"evil"}'.bytes) + '.' + parts[2]

        when:
        verifier(new StubFetcher([jwksA])).verify(tampered, ISSUER)
        then:
        thrown(InvalidTokenException)
    }

    def 'should reject an expired token' () {
        when: 'expired well beyond the 60s clock skew'
        verifier(new StubFetcher([jwksA])).verify(token(keyA, ttlMillis: -120_000L), ISSUER)
        then:
        thrown(InvalidTokenException)
    }

    def 'should reject a wrong issuer' () {
        when:
        verifier(new StubFetcher([jwksA])).verify(token(keyA, iss: 'https://evil.example.com'), ISSUER)
        then:
        thrown(UnknownIssuerException)
    }

    def 'should reject symmetric and unsupported algorithms' () {
        when:
        verifier(new StubFetcher([jwksA])).verify(JwtInspectorTest.hs256Token(), ISSUER)
        then:
        thrown(InvalidTokenException)
    }

    def 'should refetch jwks on unknown kid' () {
        given: 'the stub has no cache, so each verify fetches: two regular fetches return the old set, the refetch returns the rotated one'
        def fetcher = new StubFetcher([jwksA, jwksA, jwksB])
        def v = verifier(fetcher, new TestConfig(refreshMinInterval: Duration.ZERO))

        expect: 'prime with key-a (fetch #1)'
        v.verify(token(keyA), ISSUER)

        and: 'a token signed with rotated key-b (fetch #2 = old set, kid miss) triggers clearCache + refetch (#3) and verifies'
        v.verify(token(keyB), ISSUER).subject() == 'user-123'
        fetcher.clearCount.get() == 1
        fetcher.fetchCount.get() == 3
    }

    def 'should rate-limit refetch on unknown kid' () {
        given: 'a fetcher that always returns the OLD key set'
        def fetcher = new StubFetcher([jwksA])
        def v = verifier(fetcher)   // refreshMinInterval 60s
        v.verify(token(keyA), ISSUER)   // fetch #1

        when: 'two consecutive unknown-kid misses'
        try { v.verify(token(keyB), ISSUER) } catch (InvalidTokenException ignored) { }
        try { v.verify(token(keyB), ISSUER) } catch (InvalidTokenException ignored) { }
        then: 'only one cache-clearing refetch happened — the second miss was inside the min interval'
        fetcher.clearCount.get() == 1
    }

    def 'should fail when fetch fails' () {
        when:
        verifier(new StubFetcher([null])).verify(token(keyA), ISSUER)
        then:
        thrown(JwksUnavailableException)
    }

    def 'should normalize issuer urls' () {
        expect:
        DynamicJwksVerifier.normalizeIssuer(input) == expected

        where:
        input                             | expected
        'https://tower.example.com'       | 'https://tower.example.com'
        'https://tower.example.com/'      | 'https://tower.example.com'
        'HTTPS://Tower.Example.COM//'     | 'https://tower.example.com'
        'https://foo.com/API/'            | 'https://foo.com/API'
        null                              | null
    }
}
```

Note: the stub fetcher has no cache (unlike Micronaut's real one), so every `verify` performs a
fetch; the rate-limit invariant is asserted on `clearCount` — exactly one cache-clearing refetch
across two unknown-kid misses inside the min interval.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :lib-auth-jwks:test --tests 'DynamicJwksVerifierTest'`
Expected: FAIL (classes not found)

- [ ] **Step 3: Write the implementation**

`JwksVerifierConfig.java`:

```java
package io.seqera.auth.jwks;

import java.time.Duration;

/**
 * Settings for dynamic JWKS verification. Implemented by the consuming
 * service (same pattern as lib-pairing's PairingConfig).
 */
public interface JwksVerifierConfig {

    /** Path of the JWKS document relative to the issuer endpoint, e.g. {@code /.well-known/jwks.json} */
    String getJwksPath();

    /** Minimum interval between JWKS re-fetches triggered by an unknown {@code kid} */
    Duration getRefreshMinInterval();

    /** Accepted clock skew for {@code exp}/{@code nbf} validation */
    Duration getClockSkew();

    /** Maximum time to wait for a JWKS fetch */
    Duration getFetchTimeout();
}
```

`VerifiedJwt.java`:

```java
package io.seqera.auth.jwks;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable view over the claims of a successfully verified JWT.
 */
public record VerifiedJwt(
        String issuer,
        String subject,
        Instant expiration,
        Map<String, Object> claims ) {
}
```

`exception/InvalidTokenException.java` (repeat the same shape for the other two):

```java
package io.seqera.auth.jwks.exception;

/**
 * The token failed cryptographic or claims validation.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

`exception/UnknownIssuerException.java` — same body, class name `UnknownIssuerException`, javadoc: "The token's `iss` claim does not match the expected issuer."

`exception/JwksUnavailableException.java` — same body, class name `JwksUnavailableException`, javadoc: "The JWKS document could not be retrieved."

`DynamicJwksVerifier.java`:

```java
package io.seqera.auth.jwks;

import java.text.ParseException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import io.micronaut.context.annotation.Requires;
import io.micronaut.security.token.jwt.signature.jwks.JwkSetFetcher;
import io.micronaut.security.token.jwt.signature.jwks.JwkValidator;
import io.seqera.auth.jwks.exception.InvalidTokenException;
import io.seqera.auth.jwks.exception.JwksUnavailableException;
import io.seqera.auth.jwks.exception.UnknownIssuerException;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/**
 * Verifies JWTs against the JWKS document of a dynamically-resolved issuer.
 *
 * Micronaut Security's {@code SignatureConfiguration} beans are fixed at
 * startup; this verifier resolves the JWKS URL per request from the expected
 * issuer endpoint instead, while reusing Micronaut's own machinery:
 * {@link JwkSetFetcher} for retrieval and caching (and, through it, the
 * {@code JwksClient} transport seam) and {@link JwkValidator} for signature
 * verification. An unknown {@code kid} triggers a rate-limited cache clear +
 * re-fetch to pick up key rotation.
 */
@Singleton
@Requires(beans = JwksVerifierConfig.class)
public class DynamicJwksVerifier {

    static final Set<JWSAlgorithm> ALLOWED_ALGS = Set.of(
            JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512,
            JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512 );

    private final JwkSetFetcher<JWKSet> jwkSetFetcher;
    private final JwkValidator jwkValidator;
    private final JwksVerifierConfig config;
    private final Map<String, Instant> lastRefresh = new ConcurrentHashMap<>();

    public DynamicJwksVerifier(JwkSetFetcher<JWKSet> jwkSetFetcher, JwkValidator jwkValidator, JwksVerifierConfig config) {
        this.jwkSetFetcher = jwkSetFetcher;
        this.jwkValidator = jwkValidator;
        this.config = config;
    }

    public VerifiedJwt verify(String token, String expectedIssuer) {
        final SignedJWT jwt = parse(token);
        final JWSAlgorithm alg = jwt.getHeader().getAlgorithm();
        if (!ALLOWED_ALGS.contains(alg))
            throw new InvalidTokenException("Unsupported JWT signature algorithm: " + alg);

        final String issuer = normalizeIssuer(expectedIssuer);
        final String url = jwksUrl(issuer);
        JWKSet jwks = fetchJwks(issuer, url);
        final String kid = jwt.getHeader().getKeyID();
        if (kid != null && jwks.getKeyByKeyId(kid) == null) {
            final JWKSet refreshed = refreshJwks(issuer, url);
            if (refreshed != null)
                jwks = refreshed;
        }

        if (!verifySignature(jwt, jwks))
            throw new InvalidTokenException("JWT signature verification failed");

        final JWTClaimsSet claims = verifyClaims(jwt);
        final String iss = claims.getIssuer();
        if (iss == null || !normalizeIssuer(iss).equals(issuer))
            throw new UnknownIssuerException("JWT issuer '" + iss + "' does not match expected issuer '" + expectedIssuer + "'");

        return new VerifiedJwt(
                iss,
                claims.getSubject(),
                claims.getExpirationTime() != null ? claims.getExpirationTime().toInstant() : null,
                claims.getClaims() );
    }

    private SignedJWT parse(String token) {
        try {
            return SignedJWT.parse(token);
        }
        catch (ParseException e) {
            throw new InvalidTokenException("Malformed JWT token", e);
        }
    }

    private String jwksUrl(String issuer) {
        final String path = config.getJwksPath();
        return path.startsWith("/") ? issuer + path : issuer + "/" + path;
    }

    private JWKSet fetchJwks(String providerName, String url) {
        final JWKSet result;
        try {
            result = Mono.from(jwkSetFetcher.fetch(providerName, url))
                    .block(config.getFetchTimeout());
        }
        catch (RuntimeException e) {
            throw new JwksUnavailableException("Failed to fetch JWKS from " + url, e);
        }
        if (result == null || result.getKeys().isEmpty())
            throw new JwksUnavailableException("No JWKS keys available from " + url);
        return result;
    }

    private JWKSet refreshJwks(String providerName, String url) {
        final Instant last = lastRefresh.get(providerName);
        if (last != null && last.plus(config.getRefreshMinInterval()).isAfter(Instant.now()))
            return null;    // rate limited — keep the current key set
        lastRefresh.put(providerName, Instant.now());
        jwkSetFetcher.clearCache(url);
        try {
            return fetchJwks(providerName, url);
        }
        catch (JwksUnavailableException e) {
            return null;
        }
    }

    private boolean verifySignature(SignedJWT jwt, JWKSet jwks) {
        final List<JWK> matches = new JWKSelector(JWKMatcher.forJWSHeader(jwt.getHeader())).select(jwks);
        for (JWK jwk : matches) {
            if (jwkValidator.validate(jwt, jwk))
                return true;
        }
        return false;
    }

    private JWTClaimsSet verifyClaims(SignedJWT jwt) {
        try {
            final DefaultJWTClaimsVerifier<SecurityContext> verifier = new DefaultJWTClaimsVerifier<>(null, Set.of("exp"));
            verifier.setMaxClockSkew((int) config.getClockSkew().toSeconds());
            final JWTClaimsSet claims = jwt.getJWTClaimsSet();
            verifier.verify(claims, null);
            return claims;
        }
        catch (ParseException | BadJWTException e) {
            throw new InvalidTokenException("JWT validation failed - cause: " + e.getMessage(), e);
        }
    }

    /**
     * Normalize an issuer/endpoint URL for comparison: trim, drop trailing
     * slashes, lower-case the scheme and host (path is case-sensitive).
     */
    public static String normalizeIssuer(String endpoint) {
        if (endpoint == null)
            return null;
        String result = endpoint.trim().replaceAll("/+$", "");
        final int sep = result.indexOf("://");
        if (sep != -1) {
            final int pathStart = result.indexOf('/', sep + 3);
            final String head = pathStart == -1 ? result : result.substring(0, pathStart);
            final String tail = pathStart == -1 ? "" : result.substring(pathStart);
            result = head.toLowerCase() + tail;
        }
        return result;
    }
}
```

`lib-auth-jwks/changelog.txt`:

```
0.1.0 - 22 Jul 2026
- Initial release: DynamicJwksVerifier (multi-issuer JWKS verification over
  Micronaut Security's JwkSetFetcher/JwkValidator/JwksClient machinery), JwtInspector
```

- [ ] **Step 4: Run the full module test suite**

Run: `./gradlew :lib-auth-jwks:test`
Expected: PASS (all specs)

- [ ] **Step 5: Publish to maven local for the Wave tasks**

Run: `./gradlew :lib-auth-jwks:publishToMavenLocal`
Expected: BUILD SUCCESSFUL. (The final Wave release requires `lib-auth-jwks:0.1.0` published to maven.seqera.io via the libseqera release process — the Wave PR must not merge before that.)

- [ ] **Step 6: Commit (libseqera repo)**

```bash
git add lib-auth-jwks
git commit -m "feat: lib-auth-jwks dynamic multi-issuer JWKS verifier"
```

---

### Task 3: Wave — dependencies + PairingJwksClient (JWKS over the pairing tunnel)

**Files:**
- Modify: `/Users/pditommaso/Projects/wave/build.gradle` (dependencies block, next to the other `io.seqera:` entries around line 48; repositories block at line 22 for local dev only)
- Modify: `src/main/resources/application.yml` (disable Micronaut's HTTP JWKS client)
- Create: `src/main/groovy/io/seqera/wave/auth/JwksAuthConfig.groovy`
- Create: `src/main/groovy/io/seqera/wave/auth/PairingJwksClient.groovy`
- Test: `src/test/groovy/io/seqera/wave/auth/PairingJwksClientTest.groovy`

**Interfaces:**
- Consumes: `io.micronaut.security.token.jwt.signature.jwks.JwksClient` — `Publisher<String> load(@Nullable String providerName, @NonNull String url)`; `JwksVerifierConfig`, `JwksUnavailableException` (lib); `TowerConnector.sendAsync(String endpoint, ProxyHttpRequest): CompletableFuture<ProxyHttpResponse>` (existing, `TowerConnector.groovy:322`); `io.seqera.service.pairing.socket.msg.ProxyHttpRequest` (fields: `msgId`, `method`, `uri`, `auth`, `body`, `headers`); `static io.seqera.random.LongRndKey.rndHex` (same import `TowerConnector.groovy:54` uses).
- Produces: `JwksAuthConfig` bean implementing `JwksVerifierConfig` plus the Wave-only `enabled` flag; `PairingJwksClient` — the sole `JwksClient` bean (Micronaut's `HttpClientJwksClient` disabled by config), which makes both Micronaut's `DefaultJwkSetFetcher` and lib's `DynamicJwksVerifier` route JWKS retrieval through the pairing tunnel.

- [ ] **Step 1: Add the dependencies and config**

In `build.gradle` dependencies, after `implementation 'io.seqera:lib-pairing:1.0.0'`:

```gradle
implementation 'io.seqera:lib-auth-jwks:0.1.0'
implementation 'io.micronaut.security:micronaut-security-jwt'
```

(`micronaut-security-jwt` is unversioned — managed by the Micronaut platform BOM like the existing `micronaut-security` entry.)

For local development only (drop before merge): add `mavenLocal()` as the FIRST entry of the `repositories` block so the locally-published `0.1.0` resolves.

In `application.yml`, under the existing `micronaut.security` section (around line 50), add:

```yaml
    token:
      jwt:
        signatures:
          jwks-client:
            http-client:
              enabled: false
```

(This disables `HttpClientJwksClient` so `PairingJwksClient` becomes the sole `JwksClient` bean — otherwise the `JwkSetFetcher` injection of `JwksClient` would be ambiguous.)

Run: `cd /Users/pditommaso/Projects/wave && ./gradlew compileGroovy`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Write the failing test**

`src/test/groovy/io/seqera/wave/auth/PairingJwksClientTest.groovy` (copy the Wave license header):

```groovy
package io.seqera.wave.auth

import java.util.concurrent.CompletableFuture

import io.seqera.auth.jwks.exception.JwksUnavailableException
import io.seqera.service.pairing.socket.msg.ProxyHttpRequest
import io.seqera.service.pairing.socket.msg.ProxyHttpResponse
import io.seqera.wave.tower.client.connector.TowerConnector
import reactor.core.publisher.Mono
import spock.lang.Specification

class PairingJwksClientTest extends Specification {

    def 'should fetch jwks via the tower connector' () {
        given:
        def connector = Mock(TowerConnector)
        def client = new PairingJwksClient(connector)

        when:
        def result = Mono.from(client.load('https://tower.example.com', 'https://tower.example.com/.well-known/jwks.json')).block()
        then:
        1 * connector.sendAsync('https://tower.example.com', _ as ProxyHttpRequest) >> { String ep, ProxyHttpRequest req ->
            assert req.uri == 'https://tower.example.com/.well-known/jwks.json'
            assert req.method == 'GET'
            assert req.auth == null
            assert req.msgId
            CompletableFuture.completedFuture(new ProxyHttpResponse(msgId: req.msgId, status: 200, body: '{"keys":[]}'))
        }
        result == '{"keys":[]}'
    }

    def 'should fail on non-200 response' () {
        given:
        def connector = Mock(TowerConnector) {
            sendAsync(_, _) >> CompletableFuture.completedFuture(new ProxyHttpResponse(msgId: 'x', status: 404, body: null))
        }
        def client = new PairingJwksClient(connector)

        when:
        Mono.from(client.load('https://tower.example.com', 'https://tower.example.com/.well-known/jwks.json')).block()
        then:
        thrown(JwksUnavailableException)
    }

    def 'should fail when provider name is missing' () {
        when:
        Mono.from(new PairingJwksClient(Mock(TowerConnector)).load(null, 'https://x/.well-known/jwks.json')).block()
        then:
        thrown(IllegalArgumentException)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests 'PairingJwksClientTest'`
Expected: FAIL (classes not found)

- [ ] **Step 4: Write the implementation**

`src/main/groovy/io/seqera/wave/auth/JwksAuthConfig.groovy`:

```groovy
package io.seqera.wave.auth

import java.time.Duration

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
import io.seqera.auth.jwks.JwksVerifierConfig
import jakarta.inject.Singleton

/**
 * Configuration for JWKS-based validation of Platform JWT tokens.
 * Implements the lib-auth-jwks config contract and adds the Wave-level
 * enable flag.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class JwksAuthConfig implements JwksVerifierConfig {

    /**
     * When {@code false} (default) all tokens follow the legacy delegation
     * path and JWKS validation is never attempted.
     */
    @Value('${wave.auth.jwks.enabled:false}')
    boolean enabled

    @Value('${wave.auth.jwks.path:`/.well-known/jwks.json`}')
    String jwksPath

    @Value('${wave.auth.jwks.refresh-min-interval:60s}')
    Duration refreshMinInterval

    @Value('${wave.auth.jwks.clock-skew:60s}')
    Duration clockSkew

    @Value('${wave.auth.jwks.fetch-timeout:30s}')
    Duration fetchTimeout
}
```

`src/main/groovy/io/seqera/wave/auth/PairingJwksClient.groovy`:

```groovy
package io.seqera.wave.auth

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import io.micronaut.security.token.jwt.signature.jwks.JwksClient
import io.seqera.auth.jwks.exception.JwksUnavailableException
import io.seqera.service.pairing.socket.msg.ProxyHttpRequest
import io.seqera.service.pairing.socket.msg.ProxyHttpResponse
import io.seqera.wave.tower.client.connector.TowerConnector
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

import static io.seqera.random.LongRndKey.rndHex

/**
 * Micronaut {@link JwksClient} that retrieves a Platform JWKS document
 * through the Tower connector, i.e. over the pairing websocket tunnel
 * (or direct HTTP when the legacy connector is active). This is what makes
 * JWKS work for Platform instances behind private networks.
 *
 * The provider name carries the Tower endpoint used for tunnel routing.
 * Micronaut's own HTTP JWKS client is disabled in application.yml so this
 * is the sole {@link JwksClient} bean.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class PairingJwksClient implements JwksClient {

    private final TowerConnector connector

    PairingJwksClient(TowerConnector connector) {
        this.connector = connector
    }

    @Override
    Publisher<String> load(@Nullable String providerName, @NonNull String url) {
        if( !providerName )
            return Mono.error(new IllegalArgumentException("Missing JWKS provider name - it should hold the Tower endpoint for pairing channel routing"))
        final request = new ProxyHttpRequest(
                msgId: rndHex(),
                method: 'GET',
                uri: url )
        log.debug "Fetching JWKS document from '$url'"
        return Mono
                .fromFuture(connector.sendAsync(providerName, request))
                .map { ProxyHttpResponse resp ->
                    if( resp.status != 200 )
                        throw new JwksUnavailableException("Unexpected status ${resp.status} fetching JWKS from '$url'")
                    return resp.body
                }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests 'PairingJwksClientTest'`
Expected: PASS

- [ ] **Step 6: Commit (wave repo)**

```bash
cd /Users/pditommaso/Projects/wave
git add build.gradle src/main/resources/application.yml src/main/groovy/io/seqera/wave/auth src/test/groovy/io/seqera/wave/auth
git commit -m "feat: JWKS client over the pairing channel and jwks auth config"
```

---

### Task 4: Wave — UserServiceImpl wiring (the flag-gated dual path)

**Files:**
- Modify: `src/main/groovy/io/seqera/wave/service/UserServiceImpl.groovy` (whole class shown below)
- Test: `src/test/groovy/io/seqera/wave/service/UserServiceImplJwksTest.groovy`

**Interfaces:**
- Consumes: `DynamicJwksVerifier`, `JwtInspector`, `VerifiedJwt`, lib exceptions; `JwksAuthConfig` (Task 3); existing `TowerClient.userInfo(String, JwtAuth): GetUserInfoResponse` (`TowerClient.groovy:70`) and `UnauthorizedException`. The `DynamicJwksVerifier` bean comes from the lib (it activates because Wave's `JwksAuthConfig` satisfies its `@Requires(beans = JwksVerifierConfig.class)`).
- Produces: the dual-path behavior in `UserServiceImpl.getUserByAccessToken` — the ONLY behavioral change in Wave, covering both `ContainerController` and `InspectController` since both resolve identity through this method.

- [ ] **Step 1: Write the failing test**

`src/test/groovy/io/seqera/wave/service/UserServiceImplJwksTest.groovy` — uses Groovy direct field access (`service.@field`) to inject stubs without refactoring the service:

```groovy
package io.seqera.wave.service

import java.security.KeyPairGenerator

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.seqera.auth.jwks.DynamicJwksVerifier
import io.seqera.auth.jwks.VerifiedJwt
import io.seqera.auth.jwks.exception.InvalidTokenException
import io.seqera.auth.jwks.exception.JwksUnavailableException
import io.seqera.wave.auth.JwksAuthConfig
import io.seqera.wave.exception.UnauthorizedException
import io.seqera.wave.tower.User
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.client.GetUserInfoResponse
import io.seqera.wave.tower.client.TowerClient
import spock.lang.Specification

class UserServiceImplJwksTest extends Specification {

    static final String ENDPOINT = 'https://tower.example.com'
    // a structurally valid RS256 JWT (not verifiable — only parsed by JwtInspector)
    static final String RS256_TOKEN = rs256Token()

    static String rs256Token() {
        final keyPair = KeyPairGenerator.getInstance('RSA').tap { initialize(2048) }.generateKeyPair()
        final jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID('k1').build(),
                new JWTClaimsSet.Builder().subject('user-1').build() )
        jwt.sign(new RSASSASigner(keyPair.private))
        return jwt.serialize()
    }

    static JwtAuth auth(String token) {
        // positional @Canonical constructor: (key, endpoint, bearer, refresh, createdAt, updatedAt)
        return new JwtAuth('test-key', ENDPOINT, token)
    }

    private UserServiceImpl service(Map opts) {
        final result = new UserServiceImpl()
        result.@towerClient = opts.towerClient as TowerClient
        result.@jwksConfig = opts.config as JwksAuthConfig
        result.@jwksVerifier = opts.verifier as DynamicJwksVerifier
        return result
    }

    def 'should verify asymmetric jwt via jwks when enabled' () {
        given:
        def verifier = Mock(DynamicJwksVerifier)
        def towerClient = Mock(TowerClient)
        def srv = service(config: new JwksAuthConfig(enabled: true), verifier: verifier, towerClient: towerClient)

        when:
        def user = srv.getUserByAccessToken(ENDPOINT, auth(RS256_TOKEN))
        then:
        1 * verifier.verify(RS256_TOKEN, ENDPOINT) >> new VerifiedJwt(ENDPOINT, 'user-123', null, [:])
        1 * towerClient.userInfo(ENDPOINT, _) >> new GetUserInfoResponse(user: new User(id: 1))
        user.id == 1
    }

    def 'should reject invalid jwt when enabled' () {
        given:
        def verifier = Mock(DynamicJwksVerifier) {
            verify(_, _) >> { throw new InvalidTokenException('bad signature') }
        }
        def towerClient = Mock(TowerClient)
        def srv = service(config: new JwksAuthConfig(enabled: true), verifier: verifier, towerClient: towerClient)

        when:
        srv.getUserByAccessToken(ENDPOINT, auth(RS256_TOKEN))
        then:
        thrown(UnauthorizedException)
        0 * towerClient.userInfo(_, _)
    }

    def 'should reject when jwks is unavailable and token is asymmetric jwt' () {
        given:
        def verifier = Mock(DynamicJwksVerifier) {
            verify(_, _) >> { throw new JwksUnavailableException('no route') }
        }
        def towerClient = Mock(TowerClient)
        def srv = service(config: new JwksAuthConfig(enabled: true), verifier: verifier, towerClient: towerClient)

        when:
        srv.getUserByAccessToken(ENDPOINT, auth(RS256_TOKEN))
        then:
        thrown(UnauthorizedException)
        0 * towerClient.userInfo(_, _)
    }

    def 'should skip jwks for opaque tokens even when enabled' () {
        given:
        def verifier = Mock(DynamicJwksVerifier)
        def towerClient = Mock(TowerClient)
        def srv = service(config: new JwksAuthConfig(enabled: true), verifier: verifier, towerClient: towerClient)

        when:
        def user = srv.getUserByAccessToken(ENDPOINT, auth('opaque-personal-access-token'))
        then:
        0 * verifier.verify(_, _)
        1 * towerClient.userInfo(ENDPOINT, _) >> new GetUserInfoResponse(user: new User(id: 2))
        user.id == 2
    }

    def 'should skip jwks when disabled' () {
        given:
        def verifier = Mock(DynamicJwksVerifier)
        def towerClient = Mock(TowerClient)
        def srv = service(config: new JwksAuthConfig(enabled: false), verifier: verifier, towerClient: towerClient)

        when:
        srv.getUserByAccessToken(ENDPOINT, auth(RS256_TOKEN))
        then:
        0 * verifier.verify(_, _)
        1 * towerClient.userInfo(ENDPOINT, _) >> new GetUserInfoResponse(user: new User(id: 3))
    }
}
```

Note: `Mock(DynamicJwksVerifier)` mocks a concrete class — Spock handles it via the bytecode
library already on the Wave test classpath; the class has a public constructor and non-final
methods. The `rs256Token()` helper is inlined because the lib's test classes are not on Wave's
test classpath.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'UserServiceImplJwksTest'`
Expected: FAIL (no `jwksConfig`/`jwksVerifier` fields on `UserServiceImpl`)

- [ ] **Step 3: Write the implementation**

`UserServiceImpl.groovy` — replace the class body (keep the existing license header):

```groovy
package io.seqera.wave.service

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.annotation.Nullable
import io.seqera.auth.jwks.DynamicJwksVerifier
import io.seqera.auth.jwks.JwtInspector
import io.seqera.auth.jwks.exception.InvalidTokenException
import io.seqera.auth.jwks.exception.JwksUnavailableException
import io.seqera.auth.jwks.exception.UnknownIssuerException
import io.seqera.wave.auth.JwksAuthConfig
import io.seqera.wave.exception.UnauthorizedException
import io.seqera.wave.tower.User
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.client.TowerClient
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Define a service to access a Tower user
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Slf4j
@Singleton
class UserServiceImpl implements UserService {

    @Inject
    @Nullable
    private TowerClient towerClient

    @Inject
    private JwksAuthConfig jwksConfig

    @Inject
    @Nullable
    private DynamicJwksVerifier jwksVerifier

    @Override
    User getUserByAccessToken(String endpoint, JwtAuth auth) {
        // when enabled, tokens shaped as asymmetric-signed JWTs are verified
        // locally against the issuer JWKS; opaque PATs and HS256 tokens keep
        // following the legacy delegation path below
        if( jwksConfig.enabled && jwksVerifier && JwtInspector.isAsymmetricJwt(auth.bearer) ) {
            verifyJwksToken(endpoint, auth.bearer)
        }
        final resp = towerClient.userInfo(endpoint, auth)
        if (!resp || !resp.user)
            throw new UnauthorizedException("Unauthorized - Make sure you have provided a valid access token")
        log.debug("Authorized user=$resp.user")
        return resp.user
    }

    protected void verifyJwksToken(String endpoint, String token) {
        try {
            final verified = jwksVerifier.verify(token, endpoint)
            log.trace "JWKS token verification OK - issuer '${verified.issuer()}'; subject '${verified.subject()}'"
        }
        catch (JwksUnavailableException e) {
            log.warn "Unable to retrieve JWKS keys for endpoint '$endpoint' - cause: ${e.message}"
            throw new UnauthorizedException("Unable to validate access token for Tower endpoint '$endpoint'")
        }
        catch (InvalidTokenException | UnknownIssuerException e) {
            log.debug "JWKS token verification failed for endpoint '$endpoint' - cause: ${e.message}"
            throw new UnauthorizedException("Invalid access token for Tower endpoint '$endpoint'")
        }
    }
}
```

Note: a JWKS-verified failure MUST reject — never fall through to the delegation path (spec: "no silent fallback"). The `/user-info` call after successful verification is intentional in phase 1: it supplies the `User` entity that `PlatformId` needs; dropping it is phase 2, out of scope here.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'UserServiceImplJwksTest'`
Expected: PASS

- [ ] **Step 5: Run the impacted existing suites**

Run: `./gradlew test --tests 'UserServiceImplTest' --tests 'ContainerControllerTest' --tests 'InspectControllerTest'`
Expected: PASS (flag defaults to false → zero behavior change; if any of those test classes don't exist under exactly these names, run the closest existing ones touching `UserService`/the two controllers)

- [ ] **Step 6: Commit (wave repo)**

```bash
git add src/main/groovy/io/seqera/wave/service/UserServiceImpl.groovy src/test/groovy/io/seqera/wave/service/UserServiceImplJwksTest.groovy
git commit -m "feat: flag-gated JWKS validation of Platform JWT tokens"
```

---

### Task 5: Full regression + docs touch-up

**Files:**
- Modify: `src/main/resources/application.yml` (documentation-only commented block)
- Modify: `docs/superpowers/specs/2026-07-22-jwks-multi-issuer-auth-design.md` (only if implementation deviated — keep the spec truthful)

- [ ] **Step 1: Full Wave test suite**

Run: `cd /Users/pditommaso/Projects/wave && ./gradlew test`
Expected: PASS. Pay special attention to security-related suites: adding `micronaut-security-jwt` to the classpath activates Micronaut's JWT token readers in the security filter. Wave's config (`intercept-url-map` `/**` → `isAnonymous()`, basic-auth on admin endpoints) must behave exactly as before — if any basic-auth or anonymous-route test regresses, disable the unused JWT filter pieces via config (e.g. `micronaut.security.token.jwt.bearer.enabled: false`) rather than code.

- [ ] **Step 2: Full lib test suite**

Run: `cd /Users/pditommaso/Projects/libseqera && ./gradlew :lib-auth-jwks:check`
Expected: PASS

- [ ] **Step 3: Add the config reference block**

In `application.yml`, next to the existing `wave:` documentation patterns, add a commented reference:

```yaml
# JWKS-based validation of Platform JWT tokens (disabled by default).
# When enabled, asymmetric-signed JWTs are verified locally against the
# issuer JWKS fetched over the pairing channel; PATs and HS256 tokens
# keep using the legacy /user-info delegation path.
# JWKS documents are cached in-memory by Micronaut's JwkSetFetcher (60s);
# for distributed caching configure a Micronaut cache named 'jwks'
# (see libseqera micronaut-cache-redis) — CacheableJwkSetFetcher activates
# automatically.
#wave:
#  auth:
#    jwks:
#      enabled: true
#      path: '/.well-known/jwks.json'
#      refresh-min-interval: 60s
#      clock-skew: 60s
#      fetch-timeout: 30s
```

- [ ] **Step 4: Commit (wave repo)**

```bash
git add src/main/resources/application.yml docs/
git commit -m "docs: JWKS auth configuration reference"
```

---

## Out of scope (deliberate)

- **Platform-side work (P3/P4 in the spec):** a generic token-exchange grant (or RS256
  launch tokens) and making `TOWER_OIDC_PEM_PATH` required. The JWKS endpoint, RS256 signing
  and `iss` claim already exist in the `platform-oidc` module. Nothing in this plan delivers
  user-visible value until P3/P4 ship; the Wave code is flag-off inert until then.
- **Phase 2** (identity from claims, dropping `/user-info` for verified JWTs) and **Phase 3**
  (`Authorization: Bearer` header migration).
- **Distributed JWKS caching:** available later by configuring a Micronaut `jwks` cache
  (libseqera `micronaut-cache-redis`) — zero code changes, so not part of this plan.
- **End-to-end integration test** with a mock Platform over a real pairing websocket: verified
  in staging during rollout instead; the seams are individually covered by the tests above.
- **lib-auth-jwks release to maven.seqera.io:** follows the standard libseqera release process;
  the Wave PR depends on it and must not merge first (and the temporary `mavenLocal()` entry
  must be dropped from `build.gradle` before merge).
