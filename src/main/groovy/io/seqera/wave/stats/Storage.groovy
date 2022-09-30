package io.seqera.wave.stats

import groovy.transform.CompileStatic


/**
 * A storage for statistic datas
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@CompileStatic
interface Storage {

    void addBuild(BuildBean build)

}
