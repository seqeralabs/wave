package io.seqera.wave.service.persistence

import groovy.transform.CompileStatic

/**
 * A storage for statistic data
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@CompileStatic
interface PersistenceService {

    void saveBuild(BuildRecord build)

}
