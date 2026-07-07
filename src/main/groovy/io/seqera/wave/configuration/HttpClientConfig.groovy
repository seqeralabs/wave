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

import java.time.Duration
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.seqera.util.retry.Retryable
import io.seqera.wave.http.HttpClientFactory
import io.seqera.wave.http.HttpProxyConfig
/**
 * Model  Http Client settings
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Context
@Slf4j
class HttpClientConfig implements Retryable.Config {

    @Value('${wave.httpclient.connectTimeout:20s}')
    Duration connectTimeout

    @Value('${wave.httpclient.retry.delay:1s}')
    Duration retryDelay

    @Value('${wave.httpclient.retry.maxDelay}')
    @Nullable
    Duration retryMaxDelay

    @Value('${wave.httpclient.retry.attempts:3}')
    int retryAttempts

    @Value('${wave.httpclient.retry.multiplier:2.0}')
    double retryMultiplier

    @Value('${wave.httpclient.retry.jitter:0.25}')
    double retryJitter

    @Value('${wave.httpclient.streamThreshold:65536}')
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
     * Resolve the egress proxy settings from the {@code wave.httpclient.proxy.*} configuration,
     * falling back to the {@code HTTPS_PROXY}/{@code HTTP_PROXY}/{@code NO_PROXY} environment
     * variables when no explicit setting is provided
     *
     * @return The resolved {@link HttpProxyConfig} or {@code null} when no proxy is defined
     */
    HttpProxyConfig proxyConfig() {
        return proxyUri
                ? HttpProxyConfig.parse(proxyUri, proxyUsername, proxyPassword, proxyNoProxy)
                : HttpProxyConfig.fromEnvironment()
    }

    @PostConstruct
    private void init() {
        log.info "Http client config: connectTimeout=$connectTimeout; retryAttempts=$retryAttempts; retryDelay=$retryDelay; retryMaxDelay=$retryMaxDelay; retryMultiplier=$retryMultiplier; streamThreshold=$streamThreshold"
        final proxy = proxyConfig()
        HttpClientFactory.setProxyConfig(proxy)
        if( proxy ) {
            log.info "Http client proxy config: $proxy"
            if( proxy.username )
                enableProxyAuthSchemes()
        }
    }

    /**
     * By default the JDK disables the Basic scheme for proxy authentication over HTTPS
     * tunnelling (CONNECT requests) via the {@code jdk.http.auth.tunneling.disabledSchemes}
     * system property. When an authenticating proxy is configured, default these properties
     * to empty so that Basic credentials can be sent to the proxy, unless the operator has
     * already set them e.g. via {@code JAVA_TOOL_OPTIONS}
     */
    static protected void enableProxyAuthSchemes() {
        for( String name : List.of('jdk.http.auth.tunneling.disabledSchemes', 'jdk.http.auth.proxying.disabledSchemes') ) {
            if( System.getProperty(name) == null ) {
                System.setProperty(name, '')
                log.info "Setting system property '$name' to empty string to enable Basic proxy authentication"
            }
            else {
                log.debug "System property '$name' already set to '${System.getProperty(name)}'"
            }
        }
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
