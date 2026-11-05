/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.configuration

import java.net.Authenticator
import java.time.Duration
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.seqera.util.net.ProxyConfig
import io.seqera.util.retry.Retryable
import io.seqera.wave.http.HttpClientFactory
/**
 * Model  Http Client settings
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Context
@Slf4j
class HttpClientConfig implements Retryable.Config {

    @Value('${wave.httpclient.connect-timeout:20s}')
    Duration connectTimeout

    @Value('${wave.httpclient.retry.delay:1s}')
    Duration retryDelay

    @Value('${wave.httpclient.retry.max-delay}')
    @Nullable
    Duration retryMaxDelay

    @Value('${wave.httpclient.retry.attempts:3}')
    int retryAttempts

    @Value('${wave.httpclient.retry.multiplier:2.0}')
    double retryMultiplier

    @Value('${wave.httpclient.retry.jitter:0.25}')
    double retryJitter

    @Value('${wave.httpclient.stream-threshold:65536}')
    private int streamThreshold

    @Value('${wave.httpclient.proxy.uri}')
    @Nullable
    private String proxyUri

    @Value('${wave.httpclient.proxy.username}')
    @Nullable
    private String proxyUsername

    @Value('${wave.httpclient.proxy.password}')
    @Nullable
    private String proxyPassword

    @Value('${wave.httpclient.proxy.no-proxy}')
    @Nullable
    private String proxyNoProxy

    /**
     * Resolve the egress proxy, falling back to the {@code HTTPS_PROXY}/{@code HTTP_PROXY}/
     * {@code NO_PROXY} environment variables when no explicit {@code wave.httpclient.proxy.*}
     * setting is provided.
     *
     * <p>When resolved from the environment the proxy is installed JVM-wide — per-protocol
     * {@code <proto>.proxyHost}/{@code .proxyPort} and {@code http.nonProxyHosts} system properties
     * plus a default {@link Authenticator} — mirroring Nextflow (and standard tooling), so all
     * outbound HTTP in the process that honours the JVM proxy settings uses it. The explicit
     * {@code wave.httpclient.proxy.*} settings instead apply only to Wave's own HTTP clients.
     *
     * @return The resolved {@link ProxyConfig} or {@code null} when no proxy is defined
     */
    private ProxyConfig proxyConfig() {
        if( proxyUri )
            return ProxyConfig.fromUri(proxyUri, proxyUsername, proxyPassword, proxyNoProxy?.tokenize(','))
        if( proxyUsername || proxyPassword || proxyNoProxy )
            log.warn "Http client proxy settings 'wave.httpclient.proxy.username/password/no-proxy' are ignored because 'wave.httpclient.proxy.uri' is not set"
        return ProxyConfig.setupFromEnvironment(System.getenv())
    }

    @PostConstruct
    private void init() {
        log.info "Http client config: connectTimeout=$connectTimeout; retryAttempts=$retryAttempts; retryDelay=$retryDelay; retryMaxDelay=$retryMaxDelay; retryMultiplier=$retryMultiplier; streamThreshold=$streamThreshold"
        final proxy = proxyConfig()
        HttpClientFactory.setProxyConfig(proxy)
        // allow Basic auth on the HTTPS CONNECT tunnel (disabled by the JDK by default) for an
        // authenticating proxy - covers the settings path; the environment path already did this
        // via setupFromEnvironment, and repeating it is an idempotent no-op
        if( proxy?.hasCredentials() )
            ProxyConfig.enableBasicProxyTunneling()
    }

    Duration getDelay() { retryDelay }

    Duration getMaxDelay() { retryMaxDelay }

    int getMaxAttempts() { retryAttempts }

    double getJitter() { retryJitter }

    int getStreamThreshold() { streamThreshold }

    double getMultiplier() {
        return retryMultiplier
    }
}
