package io.seqera.wave.configuration

import java.time.Duration
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton
/**
 * Model  Http Client settings
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Singleton
@Slf4j
class HttpClientConfig {

    @Value('${wave.httpclient.connectTimeout:20s}')
    Duration connectTimeout

    @Value('${wave.httpclient.retry.delay:1s}')
    Duration retryDelay

    @Value('${wave.httpclient.retry.maxDelay}')
    @Nullable
    Duration retryMaxDelay

    @Value('${wave.httpclient.retry.attempts:3}')
    int retryAttempts

    @Value('${wave.httpclient.retry.multiplier:1.0}')
    float retryMultiplier

    @PostConstruct
    private void init() {
        log.debug "Http client config: connectTimeout=$connectTimeout; retryAttempts=$retryAttempts; retryDelay=$retryDelay; retryMaxDelay=$retryMaxDelay; retryMultiplier=$retryMultiplier"
    }

}
