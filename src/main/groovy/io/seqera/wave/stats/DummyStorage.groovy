package io.seqera.wave.stats

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import jakarta.inject.Singleton


/**
 * A Storage implementation who only trace events. Main use case for dev/test
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Slf4j
@Singleton
@CompileStatic
class DummyStorage implements Storage{

    @Override
    void addBuild(BuildBean build) {
        log.debug "AddBuild $build"
    }
}
