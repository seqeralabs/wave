package io.seqera.wave.stats

import groovy.transform.CompileStatic


/**
 * A storage for statistic data
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@CompileStatic
interface Storage {

    void saveBuild(BuildRecord build)

}
