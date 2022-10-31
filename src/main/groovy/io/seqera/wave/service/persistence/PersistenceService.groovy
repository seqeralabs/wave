package io.seqera.wave.service.persistence

import groovy.transform.CompileStatic
import io.micronaut.runtime.event.annotation.EventListener
import io.seqera.wave.service.builder.BuildEvent

/**
 * A storage for statistic data
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@CompileStatic
interface PersistenceService {

    @EventListener
    default void onBuildEvent(BuildEvent event) {
        saveBuild(BuildRecord.fromEvent(event))
    }

    /**
     * Store a {@link BuildRecord} object in the underlying persistence layer.
     *
     * It maye be implemented in non-blocking manner therefore there's no guarantee
     * the record is accessible via #loadBuild immediately after this operation
     *
     * @param build A {@link BuildRecord} object
     */
    void saveBuild(BuildRecord build)

    /**
     * Retrieve a {@link BuildRecord} object for the given build id
     *
     * @param buildId The build id i.e. the checksum of dockerfile + condafile + repo
     * @return The corresponding {@link BuildRecord} object object
     */
    BuildRecord loadBuild(String buildId)
    
}
