package io.seqera.wave.model

import groovy.transform.Canonical
import groovy.transform.CompileStatic


/**
 * An event fired when a build has been completed
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Canonical
@CompileStatic
class BuildEvent {

    BuildRequest request
    BuildResult result

}
