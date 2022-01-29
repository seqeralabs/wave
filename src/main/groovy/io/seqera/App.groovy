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
        log.info("Starting Tower reg at http://localhost:$PORT")
        new RegServer()
                .withPort(PORT)
                .withHandler(new RegHandler())
                .start()
    }
}
