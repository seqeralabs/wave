package io.seqera.wave.configuration

import javax.annotation.Nullable

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton

/**
 * Model Wave auth config settings
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
@Singleton
@Slf4j
class AuthConfig {
    @Nullable
    @Value('${wave.auth.basic.username}')
    String userName

    @Nullable
    @Value('${wave.auth.basic.password}')
    String password

    @Nullable
    @Value('${wave.auth.basic.enabled:false}')
    boolean enabled
}
