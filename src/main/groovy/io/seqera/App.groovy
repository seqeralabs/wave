package io.seqera

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class App {

    static int PORT = 9090

    static void main(String[] args) {
        final arch = System.getenv('CLIENT_ARCH')

        if( !arch ) {
            log.info "Missing 'CLIENT_ARCH' environment variable"
            System.exit(1)
        }

        log.info("Starting Tower reg at http://localhost:$PORT [arch: $arch]")
        new RegServer()
                .withPort(PORT)
                .withHandler(new RegHandler(arch: arch))
                .start()
    }
}
