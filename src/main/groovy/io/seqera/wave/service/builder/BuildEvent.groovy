package io.seqera.wave.service.builder

import groovy.transform.CompileStatic


/**
 * An event fired when a build has been completed
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@CompileStatic
class BuildEvent {

    BuildRequest buildRequest
    BuildResult buildResult

}
