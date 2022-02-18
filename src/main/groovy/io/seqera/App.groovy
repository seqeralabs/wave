package io.seqera

import groovy.util.logging.Slf4j
import io.seqera.config.TowerConfiguration
import io.seqera.config.YamlConfiguration
import io.seqera.docker.AuthFactory

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
class App {

    static void main(String[] args) {
        TowerConfiguration regConfiguration = YamlConfiguration.newInstace()
        final arch = regConfiguration.arch

        if( !arch ) {
            log.info "Missing 'CLIENT_ARCH' environment variable"
            System.exit(1)
        }

        AuthFactory authFactory = new AuthFactory()

        log.info("Starting Tower reg at http://localhost:$regConfiguration.port [arch: $arch]")
        new RegServer()
                .withPort(regConfiguration.port)
                .withHandler(RegHandler.builder().configuration(regConfiguration).authFactory(authFactory).build())
                .start()
    }
}
