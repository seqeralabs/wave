package io.seqera

import groovy.util.logging.Slf4j

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

        log.info("Starting Tower reg at http://localhost:$regConfiguration.port [arch: $arch]")
        new RegServer()
                .withPort(regConfiguration.port)
                .withHandler(new RegHandler(regConfiguration))
                .start()
    }
}
