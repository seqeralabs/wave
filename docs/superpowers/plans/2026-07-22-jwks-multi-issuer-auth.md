# JWKS Multi-Issuer Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Local JWKS-based validation of Platform JWTs in Wave, multi-issuer, working over the pairing websocket tunnel, with the reusable core in a new libseqera module `lib-auth-jwks`.

**Architecture:** A framework-free Java library (`lib-auth-jwks`, package `io.seqera.auth.jwks`) provides token inspection and JWKS verification behind two seams: `JwksFetcher` (transport) and `JwksCacheStore` (caching). Wave plugs the pairing tunnel into the fetcher seam (`TowerConnector`) and a Redis state store into the cache seam, and gates the whole thing behind `wave.auth.jwks.enabled` inside `UserServiceImpl.getUserByAccessToken` — the single choke point both `ContainerController` and `InspectController` go through. Asymmetric JWTs get verified locally; HS256 JWTs and opaque PATs continue on today's `/user-info` delegation path untouched.

**Tech Stack:** Java 17 (lib main sources), Groovy/Spock (tests + Wave), `com.nimbusds:nimbus-jose-jwt:9.40`, Micronaut 4.x (Wave side only), libseqera `lib-data-store-state-redis`.

**Spec:** `docs/superpowers/specs/2026-07-22-jwks-multi-issuer-auth-design.md` (in the wave repo).

## Global Constraints

- Tasks 1–4 run in `/Users/pditommaso/Projects/libseqera`, tasks 5–8 in `/Users/pditommaso/Projects/wave`. Commit in the repo you changed.
- `lib-auth-jwks` must have **no Micronaut, Redis, or Wave dependency** — only `nimbus-jose-jwt` (api) and the Spock test framework that the convention plugins already provide.
- Lib module: `group 'io.seqera'`, `VERSION` file `0.1.0`, package `io.seqera.auth.jwks`, sourceCompatibility 17 (set by conventions — don't override).
- Copy the Apache-2.0 license header from any existing libseqera source file into every new libseqera file; copy the Wave (AGPL) header from any existing Wave source file into every new Wave file. Not repeated in the code blocks below.
- Allowed JWT algorithms everywhere: RS256/RS384/RS512/ES256/ES384/ES512. Never `none`, never HS*.
- Wave feature flag `wave.auth.jwks.enabled` defaults to `false` — merged code must be a no-op until enabled.
- Config defaults (from spec): path `/.well-known/jwks.json`, cache-duration `6h`, refresh-min-interval `60s`, clock-skew `60s`, fetch-timeout `30s`.
- Wave tests: `./gradlew test --tests '<ClassName>'`. Lib tests: `./gradlew :lib-auth-jwks:test`.

---

### Task 1: lib-auth-jwks module scaffold + JwtInspector

**Files:**
- Modify: `/Users/pditommaso/Projects/libseqera/settings.gradle` (add include, keep the list's rough grouping — put it after `include('lib-activator')`)
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

`lib-auth-jwks/build.gradle` (same shape as `lib-crypto/build.gradle`):

```gradle
plugins {
    id 'io.seqera.java-library-conventions'
    id 'io.seqera.groovy-library-conventions'
}

group = 'io.seqera'
version = "${project.file('VERSION').text.trim()}"

dependencies {
    api 'com.nimbusds:nimbus-jose-jwt:9.40'
}
```

`lib-auth-jwks/README.md`:

```markdown
# lib-auth-jwks

Framework-free JWKS-based JWT verification with pluggable transport and caching.

Designed for services that accept JWTs from multiple dynamically-registered issuers,
where the issuer may only be reachable over a custom channel (e.g. a reverse
websocket tunnel). Provide a `JwksFetcher` for the transport and optionally a
`JwksCacheStore` for distributed caching; `JwksJwtVerifier` does the rest.

Entry points: `JwtInspector` (cheap token-shape checks), `JwksJwtVerifier.verify(token, expectedIssuer)`.
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

### Task 2: Core types — config, results, exceptions, cache store, fetcher interface

**Files:**
- Create: `lib-auth-jwks/src/main/java/io/seqera/auth/jwks/JwksConfig.java`
- Create: `lib-auth-jwks/src/main/java/io/seqera/auth/jwks/VerifiedJwt.java`
- Create: `lib-auth-jwks/src/main/java/io/seqera/auth/jwks/CachedJwks.java`
- Create: `lib-auth-jwks/src/main/java/io/seqera/auth/jwks/JwksFetcher.java`
- Create: `lib-auth-jwks/src/main/java/io/seqera/auth/jwks/JwksCacheStore.java`
- Create: `lib-auth-jwks/src/main/java/io/seqera/auth/jwks/InMemoryJwksCacheStore.java`
- Create: `lib-auth-jwks/src/main/java/io/seqera/auth/jwks/exception/InvalidTokenException.java`
- Create: `lib-auth-jwks/src/main/java/io/seqera/auth/jwks/exception/UnknownIssuerException.java`
- Create: `lib-auth-jwks/src/main/java/io/seqera/auth/jwks/exception/JwksUnavailableException.java`
- Test: `lib-auth-jwks/src/test/groovy/io/seqera/auth/jwks/InMemoryJwksCacheStoreTest.groovy`

**Interfaces:**
- Produces (used by Tasks 3, 4, and the Wave tasks):
  - `record JwksConfig(String jwksPath, Duration cacheDuration, Duration refreshMinInterval, Duration clockSkew, Duration fetchTimeout)` + `JwksConfig.defaults()`
  - `record VerifiedJwt(String issuer, String subject, Instant expiration, Map<String,Object> claims)`
  - `record CachedJwks(String json, Instant createdAt)`
  - `interface JwksFetcher { CompletableFuture<String> fetch(String endpoint); }`
  - `interface JwksCacheStore { CachedJwks load(String endpoint); void save(String endpoint, String jwksJson); }` — `load`/`save` naming is deliberate: it avoids an erasure clash with `AbstractStateStore.get/put` in downstream implementations.
  - Exceptions: `InvalidTokenException`, `UnknownIssuerException`, `JwksUnavailableException` — all `RuntimeException`, all with `(String)` and `(String, Throwable)` constructors.

- [ ] **Step 1: Write the failing test**

`src/test/groovy/io/seqera/auth/jwks/InMemoryJwksCacheStoreTest.groovy`:

```groovy
package io.seqera.auth.jwks

import spock.lang.Specification

class InMemoryJwksCacheStoreTest extends Specification {

    def 'should save and load a jwks document' () {
        given:
        def store = new InMemoryJwksCacheStore()

        expect:
        store.load('https://tower.example.com') == null

        when:
        store.save('https://tower.example.com', '{"keys":[]}')
        def entry = store.load('https://tower.example.com')
        then:
        entry.json() == '{"keys":[]}'
        entry.createdAt() != null
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :lib-auth-jwks:test --tests 'InMemoryJwksCacheStoreTest'`
Expected: FAIL (class not found)

- [ ] **Step 3: Write the implementation**

`JwksConfig.java`:

```java
package io.seqera.auth.jwks;

import java.time.Duration;

/**
 * Settings for JWKS retrieval and JWT verification.
 */
public record JwksConfig(
        String jwksPath,
        Duration cacheDuration,
        Duration refreshMinInterval,
        Duration clockSkew,
        Duration fetchTimeout )
{
    public static JwksConfig defaults() {
        return new JwksConfig(
                "/.well-known/jwks.json",
                Duration.ofHours(6),
                Duration.ofSeconds(60),
                Duration.ofSeconds(60),
                Duration.ofSeconds(30) );
    }
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

`CachedJwks.java`:

```java
package io.seqera.auth.jwks;

import java.time.Instant;

/**
 * A cached JWKS document with its retrieval timestamp. Freshness is
 * decided by the verifier, so stores may retain stale copies as a
 * fallback for when the issuer is temporarily unreachable.
 */
public record CachedJwks(String json, Instant createdAt) {
}
```

`JwksFetcher.java`:

```java
package io.seqera.auth.jwks;

import java.util.concurrent.CompletableFuture;

/**
 * Transport seam: retrieve the raw JWKS JSON document for an issuer
 * endpoint. Implementations may use direct HTTP or any custom channel
 * (e.g. a reverse websocket tunnel).
 */
public interface JwksFetcher {

    CompletableFuture<String> fetch(String endpoint);
}
```

`JwksCacheStore.java`:

```java
package io.seqera.auth.jwks;

/**
 * Caching seam for JWKS documents, keyed by issuer endpoint.
 */
public interface JwksCacheStore {

    CachedJwks load(String endpoint);

    void save(String endpoint, String jwksJson);
}
```

`InMemoryJwksCacheStore.java`:

```java
package io.seqera.auth.jwks;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-process cache. Entries are never evicted — the set of
 * issuers a service talks to is small and bounded by design.
 */
public class InMemoryJwksCacheStore implements JwksCacheStore {

    private final Map<String, CachedJwks> cache = new ConcurrentHashMap<>();

    @Override
    public CachedJwks load(String endpoint) {
        return cache.get(endpoint);
    }

    @Override
    public void save(String endpoint, String jwksJson) {
        cache.put(endpoint, new CachedJwks(jwksJson, Instant.now()));
    }
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

`exception/JwksUnavailableException.java` — same body, class name `JwksUnavailableException`, javadoc: "The JWKS document could not be retrieved and no cached copy exists."

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :lib-auth-jwks:test --tests 'InMemoryJwksCacheStoreTest'`
Expected: PASS

- [ ] **Step 5: Commit (libseqera repo)**

```bash
git add lib-auth-jwks
git commit -m "feat: lib-auth-jwks core types, cache store and fetcher seams"
```

---

### Task 3: HttpJwksFetcher (default direct-HTTP transport)

**Files:**
- Create: `lib-auth-jwks/src/main/java/io/seqera/auth/jwks/HttpJwksFetcher.java`
- Test: `lib-auth-jwks/src/test/groovy/io/seqera/auth/jwks/HttpJwksFetcherTest.groovy`

**Interfaces:**
- Consumes: `JwksFetcher`, `JwksConfig` (`jwksPath`, `fetchTimeout`), `JwksUnavailableException` (Task 2).
- Produces: `HttpJwksFetcher(JwksConfig)` implementing `JwksFetcher`; static helper `HttpJwksFetcher.joinPath(String endpoint, String path): String`.

- [ ] **Step 1: Write the failing test** (uses the JDK's built-in `HttpServer`, no new dependency)

`src/test/groovy/io/seqera/auth/jwks/HttpJwksFetcherTest.groovy`:

```groovy
package io.seqera.auth.jwks

import java.util.concurrent.CompletionException

import com.sun.net.httpserver.HttpServer
import io.seqera.auth.jwks.exception.JwksUnavailableException
import spock.lang.Specification

class HttpJwksFetcherTest extends Specification {

    def 'should join endpoint and path' () {
        expect:
        HttpJwksFetcher.joinPath(endpoint, path) == expected

        where:
        endpoint                        | path                       | expected
        'https://foo.com'               | '/.well-known/jwks.json'   | 'https://foo.com/.well-known/jwks.json'
        'https://foo.com/'              | '/.well-known/jwks.json'   | 'https://foo.com/.well-known/jwks.json'
        'https://foo.com'               | 'oauth/certs'              | 'https://foo.com/oauth/certs'
    }

    def 'should fetch jwks document over http' () {
        given:
        def server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext('/.well-known/jwks.json') { exchange ->
            final body = '{"keys":[]}'.bytes
            exchange.sendResponseHeaders(200, body.length)
            exchange.responseBody.withCloseable { it.write(body) }
        }
        server.start()
        def fetcher = new HttpJwksFetcher(JwksConfig.defaults())

        expect:
        fetcher.fetch("http://localhost:${server.address.port}").join() == '{"keys":[]}'

        cleanup:
        server.stop(0)
    }

    def 'should fail on non-200 status' () {
        given:
        def server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext('/.well-known/jwks.json') { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
        def fetcher = new HttpJwksFetcher(JwksConfig.defaults())

        when:
        fetcher.fetch("http://localhost:${server.address.port}").join()
        then:
        def e = thrown(CompletionException)
        e.cause instanceof JwksUnavailableException

        cleanup:
        server.stop(0)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :lib-auth-jwks:test --tests 'HttpJwksFetcherTest'`
Expected: FAIL (class not found)

- [ ] **Step 3: Write the implementation**

`HttpJwksFetcher.java`:

```java
package io.seqera.auth.jwks;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import io.seqera.auth.jwks.exception.JwksUnavailableException;

/**
 * Default {@link JwksFetcher} performing a plain HTTP GET against the
 * issuer endpoint. Suitable when the issuer is directly reachable.
 */
public class HttpJwksFetcher implements JwksFetcher {

    private final HttpClient client;
    private final JwksConfig config;

    public HttpJwksFetcher(JwksConfig config) {
        this.config = config;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public CompletableFuture<String> fetch(String endpoint) {
        final String target = joinPath(endpoint, config.jwksPath());
        final HttpRequest request = HttpRequest.newBuilder(URI.create(target))
                .GET()
                .timeout(config.fetchTimeout())
                .build();
        return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() == 200)
                        return resp.body();
                    throw new JwksUnavailableException("Unexpected status " + resp.statusCode() + " fetching JWKS from " + target);
                });
    }

    static String joinPath(String endpoint, String path) {
        final String base = endpoint.replaceAll("/+$", "");
        return path.startsWith("/") ? base + path : base + "/" + path;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :lib-auth-jwks:test --tests 'HttpJwksFetcherTest'`
Expected: PASS

- [ ] **Step 5: Commit (libseqera repo)**

```bash
git add lib-auth-jwks
git commit -m "feat: lib-auth-jwks default HTTP fetcher"
```

---

### Task 4: JwksJwtVerifier — the verification engine

**Files:**
- Create: `lib-auth-jwks/src/main/java/io/seqera/auth/jwks/JwksJwtVerifier.java`
- Create: `lib-auth-jwks/changelog.txt`
- Test: `lib-auth-jwks/src/test/groovy/io/seqera/auth/jwks/JwksJwtVerifierTest.groovy`

**Interfaces:**
- Consumes: everything from Tasks 2–3.
- Produces: `JwksJwtVerifier(JwksFetcher, JwksCacheStore, JwksConfig)`; `VerifiedJwt verify(String token, String expectedIssuer)` throwing `InvalidTokenException` / `UnknownIssuerException` / `JwksUnavailableException`; static `String normalizeIssuer(String)`.

**Behavior spec (mirrors the design doc):**
1. Parse token; non-JWT or disallowed alg → `InvalidTokenException`.
2. Load JWKS for the normalized issuer endpoint: fresh cache hit → use it; stale or missing → fetch; fetch failure with any cached copy → use the stale copy; fetch failure with no copy → `JwksUnavailableException`.
3. Token `kid` absent from the key set → rate-limited re-fetch (min interval `refreshMinInterval`, per endpoint), then proceed with whatever key set we have.
4. Verify signature + `exp`/`nbf` (clock skew from config) via Nimbus `DefaultJWTProcessor`; failures → `InvalidTokenException`.
5. Compare `normalizeIssuer(iss claim)` with `normalizeIssuer(expectedIssuer)`; mismatch or missing → `UnknownIssuerException`.

- [ ] **Step 1: Write the failing tests**

`src/test/groovy/io/seqera/auth/jwks/JwksJwtVerifierTest.groovy`:

```groovy
package io.seqera.auth.jwks

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.seqera.auth.jwks.exception.InvalidTokenException
import io.seqera.auth.jwks.exception.JwksUnavailableException
import io.seqera.auth.jwks.exception.UnknownIssuerException
import spock.lang.Shared
import spock.lang.Specification

class JwksJwtVerifierTest extends Specification {

    static final String ISSUER = 'https://tower.example.com'

    @Shared RSAKey keyA
    @Shared RSAKey keyB
    @Shared String jwksA
    @Shared String jwksB

    def setupSpec() {
        keyA = new RSAKeyGenerator(2048).keyID('key-a').generate()
        keyB = new RSAKeyGenerator(2048).keyID('key-b').generate()
        jwksA = new JWKSet(keyA.toPublicJWK()).toString()
        jwksB = new JWKSet(keyB.toPublicJWK()).toString()
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

    static JwksFetcher fetcherOf(String... responses) {
        final count = new AtomicInteger()
        return { endpoint ->
            final i = Math.min(count.getAndIncrement(), responses.length - 1)
            final r = responses[i]
            r == null
                    ? CompletableFuture.<String>failedFuture(new JwksUnavailableException('boom'))
                    : CompletableFuture.completedFuture(r)
        } as JwksFetcher
    }

    static JwksConfig config(Map opts = [:]) {
        new JwksConfig(
                '/.well-known/jwks.json',
                (Duration) opts.getOrDefault('cacheDuration', Duration.ofHours(6)),
                (Duration) opts.getOrDefault('refreshMinInterval', Duration.ofSeconds(60)),
                Duration.ofSeconds(60),
                Duration.ofSeconds(5) )
    }

    def 'should verify a valid token' () {
        given:
        def verifier = new JwksJwtVerifier(fetcherOf(jwksA), new InMemoryJwksCacheStore(), config())

        when:
        def result = verifier.verify(token(keyA), ISSUER)
        then:
        result.issuer() == ISSUER
        result.subject() == 'user-123'
        result.expiration() != null
    }

    def 'should accept issuer with cosmetic differences' () {
        given:
        def verifier = new JwksJwtVerifier(fetcherOf(jwksA), new InMemoryJwksCacheStore(), config())

        expect:
        verifier.verify(token(keyA), 'HTTPS://Tower.Example.Com/').subject() == 'user-123'
    }

    def 'should reject a token signed by an unknown key' () {
        given: 'the JWKS only contains key-a but the token is signed with key-b'
        def verifier = new JwksJwtVerifier(fetcherOf(jwksA), new InMemoryJwksCacheStore(), config())

        when:
        verifier.verify(token(keyB), ISSUER)
        then:
        thrown(InvalidTokenException)
    }

    def 'should reject a tampered token' () {
        given:
        def verifier = new JwksJwtVerifier(fetcherOf(jwksA), new InMemoryJwksCacheStore(), config())
        def parts = token(keyA).tokenize('.')
        def tampered = parts[0] + '.' + Base64.urlEncoder.withoutPadding().encodeToString('{"sub":"evil"}'.bytes) + '.' + parts[2]

        when:
        verifier.verify(tampered, ISSUER)
        then:
        thrown(InvalidTokenException)
    }

    def 'should reject an expired token' () {
        given:
        def verifier = new JwksJwtVerifier(fetcherOf(jwksA), new InMemoryJwksCacheStore(), config())

        when: 'expired well beyond the 60s clock skew'
        verifier.verify(token(keyA, ttlMillis: -120_000L), ISSUER)
        then:
        thrown(InvalidTokenException)
    }

    def 'should reject a wrong issuer' () {
        given:
        def verifier = new JwksJwtVerifier(fetcherOf(jwksA), new InMemoryJwksCacheStore(), config())

        when:
        verifier.verify(token(keyA, iss: 'https://evil.example.com'), ISSUER)
        then:
        thrown(UnknownIssuerException)
    }

    def 'should reject symmetric and unsupported algorithms' () {
        given:
        def verifier = new JwksJwtVerifier(fetcherOf(jwksA), new InMemoryJwksCacheStore(), config())

        when:
        verifier.verify(JwtInspectorTest.hs256Token(), ISSUER)
        then:
        thrown(InvalidTokenException)
    }

    def 'should refetch jwks on unknown kid' () {
        given: 'first fetch returns the old key set, second fetch the rotated one'
        def verifier = new JwksJwtVerifier(
                fetcherOf(jwksA, jwksB),
                new InMemoryJwksCacheStore(),
                config(refreshMinInterval: Duration.ZERO) )

        expect: 'prime the cache with key-a'
        verifier.verify(token(keyA), ISSUER)

        and: 'a token signed with rotated key-b triggers a refetch and verifies'
        verifier.verify(token(keyB), ISSUER).subject() == 'user-123'
    }

    def 'should rate-limit refetch on unknown kid' () {
        given: 'a fetcher that always returns the OLD key set, counting invocations'
        def count = new AtomicInteger()
        def fetcher = { String ep ->
            count.incrementAndGet()
            CompletableFuture.completedFuture(jwksA)
        } as JwksFetcher
        def verifier = new JwksJwtVerifier(
                fetcher,
                new InMemoryJwksCacheStore(),
                config(refreshMinInterval: Duration.ofHours(1)) )
        verifier.verify(token(keyA), ISSUER)   // initial fetch (#1)

        when: 'two consecutive unknown-kid misses'
        try { verifier.verify(token(keyB), ISSUER) } catch (InvalidTokenException ignored) { }
        try { verifier.verify(token(keyB), ISSUER) } catch (InvalidTokenException ignored) { }
        then: 'only one refetch happened — the second miss was throttled'
        count.get() == 2
    }

    def 'should fall back to stale cache when fetch fails' () {
        given: 'cache duration zero means every hit is stale; second fetch fails'
        def verifier = new JwksJwtVerifier(
                fetcherOf(jwksA, null),
                new InMemoryJwksCacheStore(),
                config(cacheDuration: Duration.ZERO) )
        verifier.verify(token(keyA), ISSUER)

        expect: 'still verifies using the stale cached copy'
        verifier.verify(token(keyA), ISSUER).subject() == 'user-123'
    }

    def 'should fail when fetch fails and no cache exists' () {
        given:
        def verifier = new JwksJwtVerifier(fetcherOf(null), new InMemoryJwksCacheStore(), config())

        when:
        verifier.verify(token(keyA), ISSUER)
        then:
        thrown(JwksUnavailableException)
    }

    def 'should normalize issuer urls' () {
        expect:
        JwksJwtVerifier.normalizeIssuer(input) == expected

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

Note: in the rate-limit test both misses throw `InvalidTokenException` (the key set never contains `key-b`); the assertion is on the fetch count — initial fetch plus exactly one refetch, the second miss being inside the min interval.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :lib-auth-jwks:test --tests 'JwksJwtVerifierTest'`
Expected: FAIL (class not found)

- [ ] **Step 3: Write the implementation**

`JwksJwtVerifier.java`:

```java
package io.seqera.auth.jwks;

import java.text.ParseException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.seqera.auth.jwks.exception.InvalidTokenException;
import io.seqera.auth.jwks.exception.JwksUnavailableException;
import io.seqera.auth.jwks.exception.UnknownIssuerException;

/**
 * Verifies JWTs against the JWKS document of a dynamically-resolved issuer.
 *
 * The issuer's JWKS is retrieved through the pluggable {@link JwksFetcher}
 * and cached in the pluggable {@link JwksCacheStore}. An unknown {@code kid}
 * triggers a rate-limited re-fetch to pick up key rotation; a fetch failure
 * falls back to the last cached copy when one exists.
 */
public class JwksJwtVerifier {

    static final Set<JWSAlgorithm> ALLOWED_ALGS = Set.of(
            JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512,
            JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512 );

    private final JwksFetcher fetcher;
    private final JwksCacheStore cache;
    private final JwksConfig config;
    private final Map<String, Instant> lastRefresh = new ConcurrentHashMap<>();

    public JwksJwtVerifier(JwksFetcher fetcher, JwksCacheStore cache, JwksConfig config) {
        this.fetcher = fetcher;
        this.cache = cache;
        this.config = config;
    }

    public VerifiedJwt verify(String token, String expectedIssuer) {
        final SignedJWT jwt = parse(token);
        final JWSAlgorithm alg = jwt.getHeader().getAlgorithm();
        if (!ALLOWED_ALGS.contains(alg))
            throw new InvalidTokenException("Unsupported JWT signature algorithm: " + alg);

        final String issuer = normalizeIssuer(expectedIssuer);
        JWKSet jwks = loadJwks(issuer);
        final String kid = jwt.getHeader().getKeyID();
        if (kid != null && jwks.getKeyByKeyId(kid) == null)
            jwks = refreshJwks(issuer, jwks);

        final JWTClaimsSet claims = process(jwt, jwks);
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

    private JWKSet loadJwks(String endpoint) {
        final CachedJwks cached = cache.load(endpoint);
        final boolean fresh = cached != null
                && cached.createdAt().plus(config.cacheDuration()).isAfter(Instant.now());
        if (fresh)
            return parseJwks(cached.json());
        try {
            return fetchAndStore(endpoint);
        }
        catch (JwksUnavailableException e) {
            if (cached != null)
                return parseJwks(cached.json());   // stale fallback
            throw e;
        }
    }

    private JWKSet refreshJwks(String endpoint, JWKSet current) {
        final Instant last = lastRefresh.get(endpoint);
        if (last != null && last.plus(config.refreshMinInterval()).isAfter(Instant.now()))
            return current;    // rate limited — keep the current key set
        lastRefresh.put(endpoint, Instant.now());
        try {
            return fetchAndStore(endpoint);
        }
        catch (JwksUnavailableException e) {
            return current;
        }
    }

    private JWKSet fetchAndStore(String endpoint) {
        final String json;
        try {
            json = fetcher.fetch(endpoint).get(config.fetchTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (ExecutionException e) {
            if (e.getCause() instanceof JwksUnavailableException cause)
                throw cause;
            throw new JwksUnavailableException("Failed to fetch JWKS from " + endpoint, e.getCause());
        }
        catch (Exception e) {
            if (e instanceof InterruptedException)
                Thread.currentThread().interrupt();
            throw new JwksUnavailableException("Failed to fetch JWKS from " + endpoint, e);
        }
        cache.save(endpoint, json);
        return parseJwks(json);
    }

    private JWKSet parseJwks(String json) {
        try {
            return JWKSet.parse(json);
        }
        catch (ParseException e) {
            throw new JwksUnavailableException("Malformed JWKS document", e);
        }
    }

    private JWTClaimsSet process(SignedJWT jwt, JWKSet jwks) {
        try {
            final DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(ALLOWED_ALGS, new ImmutableJWKSet<>(jwks)));
            final DefaultJWTClaimsVerifier<SecurityContext> claimsVerifier = new DefaultJWTClaimsVerifier<>(null, Set.of("exp"));
            claimsVerifier.setMaxClockSkew((int) config.clockSkew().toSeconds());
            processor.setJWTClaimsSetVerifier(claimsVerifier);
            return processor.process(jwt, null);
        }
        catch (BadJOSEException | JOSEException e) {
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
0.1.0 - <today's date>
- Initial release: JwksJwtVerifier, JwtInspector, HttpJwksFetcher, pluggable JwksFetcher/JwksCacheStore seams
```

- [ ] **Step 4: Run the full module test suite**

Run: `./gradlew :lib-auth-jwks:test`
Expected: PASS (all specs)

- [ ] **Step 5: Publish to maven local for the Wave tasks**

Run: `./gradlew :lib-auth-jwks:publishToMavenLocal`
Expected: BUILD SUCCESSFUL. (The final Wave release requires `lib-auth-jwks:0.1.0` published to maven.seqera.io via the libseqera release process — same as every other lib bump; the Wave PR must not merge before that.)

- [ ] **Step 6: Commit (libseqera repo)**

```bash
git add lib-auth-jwks
git commit -m "feat: lib-auth-jwks JWKS-based JWT verifier"
```

---

### Task 5: Wave — dependency + PairingJwksFetcher (JWKS over the pairing tunnel)

**Files:**
- Modify: `/Users/pditommaso/Projects/wave/build.gradle` (dependencies block, next to the other `io.seqera:` entries around line 48; repositories block at line 22 for local dev only)
- Create: `src/main/groovy/io/seqera/wave/auth/JwksAuthConfig.groovy`
- Create: `src/main/groovy/io/seqera/wave/auth/PairingJwksFetcher.groovy`
- Test: `src/test/groovy/io/seqera/wave/auth/PairingJwksFetcherTest.groovy`

**Interfaces:**
- Consumes: `JwksFetcher`, `JwksUnavailableException`, `JwksConfig` (lib); `TowerConnector.sendAsync(String endpoint, ProxyHttpRequest): CompletableFuture<ProxyHttpResponse>` (existing, `TowerConnector.groovy:322`); `io.seqera.service.pairing.socket.msg.ProxyHttpRequest` (fields: `msgId`, `method`, `uri`, `auth`, `body`, `headers`); `static io.seqera.random.LongRndKey.rndHex` (same import `TowerConnector.groovy:54` uses).
- Produces: `JwksAuthConfig` bean (fields `enabled`, `path`, `cacheDuration`, `refreshMinInterval`, `clockSkew`, `fetchTimeout`; method `toJwksConfig(): JwksConfig`); `PairingJwksFetcher implements JwksFetcher`.

- [ ] **Step 1: Add the dependency**

In `build.gradle` dependencies, after `implementation 'io.seqera:lib-pairing:1.0.0'`:

```gradle
implementation 'io.seqera:lib-auth-jwks:0.1.0'
```

For local development only (NOT to be committed if the team prefers otherwise — check before merging), add `mavenLocal()` as the FIRST entry of the `repositories` block so the locally-published `0.1.0` resolves:

```gradle
repositories {
    mavenLocal()
    mavenCentral()
    ...
}
```

Run: `cd /Users/pditommaso/Projects/wave && ./gradlew compileGroovy`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Write the failing test**

`src/test/groovy/io/seqera/wave/auth/PairingJwksFetcherTest.groovy` (copy the Wave license header):

```groovy
package io.seqera.wave.auth

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

import io.seqera.auth.jwks.exception.JwksUnavailableException
import io.seqera.service.pairing.socket.msg.ProxyHttpRequest
import io.seqera.service.pairing.socket.msg.ProxyHttpResponse
import io.seqera.wave.tower.client.connector.TowerConnector
import spock.lang.Specification

class PairingJwksFetcherTest extends Specification {

    def 'should fetch jwks via the tower connector' () {
        given:
        def connector = Mock(TowerConnector)
        def config = new JwksAuthConfig(path: '/.well-known/jwks.json')
        def fetcher = new PairingJwksFetcher(connector, config)

        when:
        def result = fetcher.fetch('https://tower.example.com/').join()
        then:
        1 * connector.sendAsync('https://tower.example.com/', _ as ProxyHttpRequest) >> { String ep, ProxyHttpRequest req ->
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
        def fetcher = new PairingJwksFetcher(connector, new JwksAuthConfig(path: '/.well-known/jwks.json'))

        when:
        fetcher.fetch('https://tower.example.com').join()
        then:
        def e = thrown(CompletionException)
        e.cause instanceof JwksUnavailableException
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests 'PairingJwksFetcherTest'`
Expected: FAIL (classes not found)

- [ ] **Step 4: Write the implementation**

`src/main/groovy/io/seqera/wave/auth/JwksAuthConfig.groovy`:

```groovy
package io.seqera.wave.auth

import java.time.Duration

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
import io.seqera.auth.jwks.JwksConfig
import jakarta.inject.Singleton

/**
 * Configuration for JWKS-based validation of Platform JWT tokens.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class JwksAuthConfig {

    /**
     * When {@code false} (default) all tokens follow the legacy delegation
     * path and JWKS validation is never attempted.
     */
    @Value('${wave.auth.jwks.enabled:false}')
    boolean enabled

    @Value('${wave.auth.jwks.path:`/.well-known/jwks.json`}')
    String path

    @Value('${wave.auth.jwks.cache-duration:6h}')
    Duration cacheDuration

    @Value('${wave.auth.jwks.refresh-min-interval:60s}')
    Duration refreshMinInterval

    @Value('${wave.auth.jwks.clock-skew:60s}')
    Duration clockSkew

    @Value('${wave.auth.jwks.fetch-timeout:30s}')
    Duration fetchTimeout

    JwksConfig toJwksConfig() {
        return new JwksConfig(path, cacheDuration, refreshMinInterval, clockSkew, fetchTimeout)
    }
}
```

`src/main/groovy/io/seqera/wave/auth/PairingJwksFetcher.groovy`:

```groovy
package io.seqera.wave.auth

import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.auth.jwks.JwksFetcher
import io.seqera.auth.jwks.exception.JwksUnavailableException
import io.seqera.service.pairing.socket.msg.ProxyHttpRequest
import io.seqera.service.pairing.socket.msg.ProxyHttpResponse
import io.seqera.wave.tower.client.connector.TowerConnector
import jakarta.inject.Singleton

import static io.seqera.random.LongRndKey.rndHex

/**
 * Retrieves a Platform JWKS document through the Tower connector, i.e.
 * over the pairing websocket tunnel (or direct HTTP when the legacy
 * connector is active). This is what makes JWKS work for Platform
 * instances behind private networks.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class PairingJwksFetcher implements JwksFetcher {

    private final TowerConnector connector
    private final JwksAuthConfig config

    PairingJwksFetcher(TowerConnector connector, JwksAuthConfig config) {
        this.connector = connector
        this.config = config
    }

    @Override
    CompletableFuture<String> fetch(String endpoint) {
        final uri = endpoint.replaceAll('/+$', '') + (config.path.startsWith('/') ? config.path : '/' + config.path)
        final request = new ProxyHttpRequest(
                msgId: rndHex(),
                method: 'GET',
                uri: uri )
        log.debug "Fetching JWKS document from '$uri'"
        return connector
                .sendAsync(endpoint, request)
                .thenApply { ProxyHttpResponse resp ->
                    if( resp.status == 200 )
                        return resp.body
                    throw new JwksUnavailableException("Unexpected status ${resp.status} fetching JWKS from '$uri'")
                }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests 'PairingJwksFetcherTest'`
Expected: PASS

- [ ] **Step 6: Commit (wave repo)**

```bash
cd /Users/pditommaso/Projects/wave
git add build.gradle src/main/groovy/io/seqera/wave/auth src/test/groovy/io/seqera/wave/auth
git commit -m "feat: JWKS fetcher over the pairing channel and jwks auth config"
```

---

### Task 6: Wave — Redis-backed JWKS cache store

**Files:**
- Create: `src/main/groovy/io/seqera/wave/auth/JwksEntry.groovy`
- Create: `src/main/groovy/io/seqera/wave/auth/RedisJwksCacheStore.groovy`
- Test: `src/test/groovy/io/seqera/wave/auth/RedisJwksCacheStoreTest.groovy`

**Interfaces:**
- Consumes: `JwksCacheStore`/`CachedJwks` (lib); `io.seqera.data.store.state.AbstractStateStore` + `io.seqera.data.store.state.impl.StateProvider` and `io.seqera.serde.moshi.MoshiEncodeStrategy` (same pattern as `JwtAuthStore.groovy:34-51`).
- Produces: `RedisJwksCacheStore` bean implementing `JwksCacheStore` (note: `load`/`save` interface names deliberately avoid clashing with `AbstractStateStore.get/put`). Redis prefix `jwks-cache/v1`, retention 7 days (freshness within the retention window is decided by the verifier's `cacheDuration`).

- [ ] **Step 1: Write the failing test** (`@MicronautTest` resolves the local `StateProvider` when Redis is absent — same approach as other Wave state-store tests)

`src/test/groovy/io/seqera/wave/auth/RedisJwksCacheStoreTest.groovy`:

```groovy
package io.seqera.wave.auth

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class RedisJwksCacheStoreTest extends Specification {

    @Inject
    RedisJwksCacheStore store

    def 'should save and load a jwks document' () {
        expect:
        store.load('https://tower.example.com') == null

        when:
        store.save('https://tower.example.com', '{"keys":[]}')
        def entry = store.load('https://tower.example.com')
        then:
        entry.json() == '{"keys":[]}'
        entry.createdAt() != null
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'RedisJwksCacheStoreTest'`
Expected: FAIL (class not found)

- [ ] **Step 3: Write the implementation**

`src/main/groovy/io/seqera/wave/auth/JwksEntry.groovy`:

```groovy
package io.seqera.wave.auth

import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic

/**
 * Serializable state-store entry holding a cached JWKS document.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class JwksEntry {
    String json
    Instant createdAt
}
```

`src/main/groovy/io/seqera/wave/auth/RedisJwksCacheStore.groovy`:

```groovy
package io.seqera.wave.auth

import java.time.Duration
import java.time.Instant

import groovy.transform.CompileStatic
import io.seqera.auth.jwks.CachedJwks
import io.seqera.auth.jwks.JwksCacheStore
import io.seqera.data.store.state.AbstractStateStore
import io.seqera.data.store.state.impl.StateProvider
import io.seqera.serde.moshi.MoshiEncodeStrategy
import jakarta.inject.Singleton

/**
 * JWKS cache backed by the Wave state store (Redis in production, local
 * in dev), so all replicas share fetched key sets and retain stale
 * copies for fallback when the issuer is unreachable.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class RedisJwksCacheStore extends AbstractStateStore<JwksEntry> implements JwksCacheStore {

    RedisJwksCacheStore(StateProvider<String,String> provider) {
        super(provider, new MoshiEncodeStrategy<JwksEntry>() {})
    }

    @Override
    protected String getPrefix() {
        return 'jwks-cache/v1'
    }

    /**
     * Retention, not freshness: the verifier treats entries older than its
     * configured cache-duration as stale but may still use them as a
     * fallback when the issuer cannot be reached.
     */
    @Override
    protected Duration getDuration() {
        return Duration.ofDays(7)
    }

    @Override
    CachedJwks load(String endpoint) {
        final entry = get(endpoint)
        return entry ? new CachedJwks(entry.json, entry.createdAt) : null
    }

    @Override
    void save(String endpoint, String jwksJson) {
        put(endpoint, new JwksEntry(jwksJson, Instant.now()))
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'RedisJwksCacheStoreTest'`
Expected: PASS

- [ ] **Step 5: Commit (wave repo)**

```bash
git add src/main/groovy/io/seqera/wave/auth src/test/groovy/io/seqera/wave/auth
git commit -m "feat: Redis-backed JWKS cache store"
```

---

### Task 7: Wave — verifier bean + UserServiceImpl wiring (the flag-gated dual path)

**Files:**
- Create: `src/main/groovy/io/seqera/wave/auth/JwksAuthFactory.groovy`
- Modify: `src/main/groovy/io/seqera/wave/service/UserServiceImpl.groovy` (whole class shown below)
- Test: `src/test/groovy/io/seqera/wave/service/UserServiceImplJwksTest.groovy`

**Interfaces:**
- Consumes: `JwksJwtVerifier`, `JwtInspector`, lib exceptions; `JwksAuthConfig` (Task 5), `PairingJwksFetcher` (Task 5), `RedisJwksCacheStore` (Task 6); existing `TowerClient.userInfo(String, JwtAuth)` and `UnauthorizedException`.
- Produces: `JwksJwtVerifier` singleton bean; the dual-path behavior in `UserServiceImpl.getUserByAccessToken` — the ONLY behavioral change in Wave, covering both `ContainerController` and `InspectController` since both resolve identity through this method.

- [ ] **Step 1: Write the failing test**

`src/test/groovy/io/seqera/wave/service/UserServiceImplJwksTest.groovy` — uses Groovy direct field access (`service.@field`) to inject stubs without refactoring the service:

```groovy
package io.seqera.wave.service

import io.seqera.auth.jwks.JwksJwtVerifier
import io.seqera.auth.jwks.VerifiedJwt
import io.seqera.auth.jwks.exception.InvalidTokenException
import io.seqera.auth.jwks.exception.JwksUnavailableException
import io.seqera.wave.auth.JwksAuthConfig
import io.seqera.wave.exception.UnauthorizedException
import io.seqera.wave.tower.User
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.client.TowerClient
import io.seqera.wave.tower.client.GetUserInfoResponse
import spock.lang.Specification

import java.security.KeyPairGenerator

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT

class UserServiceImplJwksTest extends Specification {

    // a structurally valid RS256 JWT (not verifiable — only parsed by JwtInspector)
    static final String RS256_TOKEN = rs256Token()
    static final String ENDPOINT = 'https://tower.example.com'

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
        result.@jwksVerifier = opts.verifier as JwksJwtVerifier
        return result
    }

    def 'should verify asymmetric jwt via jwks when enabled' () {
        given:
        def verifier = Mock(JwksJwtVerifier)
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
        def verifier = Mock(JwksJwtVerifier) {
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
        def verifier = Mock(JwksJwtVerifier) {
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
        def verifier = Mock(JwksJwtVerifier)
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
        def verifier = Mock(JwksJwtVerifier)
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

Note: `GetUserInfoResponse` is the actual return type of `TowerClient.userInfo` (see `TowerClient.groovy:70`); the `rs256Token()` helper is inlined because the lib's test classes are not on Wave's test classpath.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'UserServiceImplJwksTest'`
Expected: FAIL (no `jwksConfig`/`jwksVerifier` fields on `UserServiceImpl`, `JwksAuthFactory` missing)

- [ ] **Step 3: Write the implementation**

`src/main/groovy/io/seqera/wave/auth/JwksAuthFactory.groovy`:

```groovy
package io.seqera.wave.auth

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Factory
import io.seqera.auth.jwks.JwksJwtVerifier
import jakarta.inject.Singleton

/**
 * Assembles the lib-auth-jwks verifier with the Wave-specific transport
 * (pairing tunnel) and cache (Redis state store) implementations.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Factory
@CompileStatic
class JwksAuthFactory {

    @Singleton
    JwksJwtVerifier jwksJwtVerifier(PairingJwksFetcher fetcher, RedisJwksCacheStore cacheStore, JwksAuthConfig config) {
        return new JwksJwtVerifier(fetcher, cacheStore, config.toJwksConfig())
    }
}
```

`UserServiceImpl.groovy` — replace the class body (keep the existing license header and add the new imports):

```groovy
package io.seqera.wave.service

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.annotation.Nullable
import io.seqera.auth.jwks.JwksJwtVerifier
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
    private JwksJwtVerifier jwksVerifier

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
git add src/main/groovy/io/seqera/wave/auth/JwksAuthFactory.groovy src/main/groovy/io/seqera/wave/service/UserServiceImpl.groovy src/test/groovy/io/seqera/wave/service/UserServiceImplJwksTest.groovy
git commit -m "feat: flag-gated JWKS validation of Platform JWT tokens"
```

---

### Task 8: Full regression + docs touch-up

**Files:**
- Modify: `src/main/resources/application.yml` (documentation-only commented block, optional)
- Modify: `docs/superpowers/specs/2026-07-22-jwks-multi-issuer-auth-design.md` (only if implementation deviated — keep the spec truthful)

- [ ] **Step 1: Full Wave test suite**

Run: `cd /Users/pditommaso/Projects/wave && ./gradlew test`
Expected: PASS. Fix any regression before proceeding (most likely candidates: Micronaut context tests that now instantiate the new beans — all new beans must be constructible with default config and no Redis).

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
#wave:
#  auth:
#    jwks:
#      enabled: true
#      path: '/.well-known/jwks.json'
#      cache-duration: 6h
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

- **Platform-side work (P1–P3 in the spec):** asymmetric signing, JWKS endpoint, `iss` claim. Nothing in this plan delivers user-visible value until Platform ships those; the Wave code is flag-off inert until then.
- **Phase 2** (identity from claims, dropping `/user-info` for verified JWTs) and **Phase 3** (`Authorization: Bearer` header migration).
- **End-to-end integration test** with a mock Platform over a real pairing websocket: verified in staging during rollout instead; the seams are individually covered by the tests above.
- **lib-auth-jwks release to maven.seqera.io:** follows the standard libseqera release process; the Wave PR depends on it and must not merge first (and the temporary `mavenLocal()` entry must be dropped from `build.gradle` before merge).
