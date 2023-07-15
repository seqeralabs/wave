package io.seqera.wave.service.cleanup

import javax.annotation.Nullable

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.service.builder.BuildResult
import jakarta.inject.Singleton

/**
 * Implement a cleanup strategy
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
@Slf4j
class CleanupStrategy {

    @Value('${wave.build.cleanup}')
    @Nullable
    String cleanup

    @Value('${wave.debug:false}')
    Boolean debugMode


    boolean shouldCleanup(BuildResult result) {
        shouldCleanup(result?.exitStatus)
    }

    boolean shouldCleanup(Integer exitStatus) {
        if( cleanup==null )
            return !debugMode
        if( cleanup == 'true' )
            return true
        if( cleanup == 'false' )
            return false
        if( cleanup.toLowerCase() == 'onsuccess' ) {
            return exitStatus==0
        }
        log.debug "Invalid cleanup value: '$cleanup'"
        return true
    }

}
