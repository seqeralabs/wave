package io.seqera

import groovy.util.logging.Slf4j
import io.seqera.config.TowerConfiguration
import io.seqera.config.YamlConfiguration
import io.seqera.controller.RegHandler
import io.seqera.controller.RegServer
import io.seqera.auth.AuthFactory

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
class App {

    static String DEFAULT_ARCH = 'amd64'

    static void main(String[] args) {
        TowerConfiguration regConfiguration = YamlConfiguration.newInstace()
        def arch = regConfiguration.arch

        if( !arch ) {
            log.info "Missing 'CLIENT_ARCH' environment variable - Fallback to '$DEFAULT_ARCH'"
            arch = DEFAULT_ARCH
        }

        AuthFactory authFactory = new AuthFactory()

        log.info("Starting Tower reg at http://localhost:$regConfiguration.port [arch: $arch]")
        new RegServer()
                .withPort(regConfiguration.port)
                .withHandler(RegHandler.builder().configuration(regConfiguration).authFactory(authFactory).build())
                .start()
    }
}
