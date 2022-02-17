package io.seqera

import groovy.util.logging.Slf4j
import io.seqera.docker.ConfigurableAuthProvider

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
class App {

    static void main(String[] args) {
        RegConfiguration regConfiguration = new RegConfiguration()
        final arch = regConfiguration.arch

        if( !arch ) {
            log.info "Missing 'CLIENT_ARCH' environment variable"
            System.exit(1)
        }

        ConfigurableAuthProvider authProvider = new ConfigurableAuthProvider(regConfiguration)

        log.info("Starting Tower reg at http://localhost:$regConfiguration.port [arch: $arch]")
        new RegServer()
                .withPort(regConfiguration.port)
                .withHandler(RegHandler.builder().configuration(regConfiguration).authProvider(authProvider).build())
                .start()
    }
}
