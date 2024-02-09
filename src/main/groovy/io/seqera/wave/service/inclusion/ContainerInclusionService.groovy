package io.seqera.wave.service.inclusion

import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.tower.PlatformId

/**
 * Define the interface for the container inclusion service which takes care of expanding
 * a list of container names into a set of layers to the added to the target request
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface ContainerInclusionService {

    SubmitContainerTokenRequest addContainerInclusions(SubmitContainerTokenRequest request, PlatformId identity)

}
