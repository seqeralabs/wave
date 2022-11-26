package io.seqera.wave.configuration

import java.time.Duration
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
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

    @PostConstruct
    private void init() {
        log.debug "Http client config: connectTimeout=$connectTimeout"
    }

}
