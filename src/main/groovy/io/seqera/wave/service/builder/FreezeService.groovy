package io.seqera.wave.service.builder

import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.tower.User

/**
 * Implement container freeze service to augment a container build at runtime
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface FreezeService {

    /**
     * Creates a new freeze build from the current container request. A freeze build
     * modifies the submitted container file adding the dependencies defined by the
     * {@link SubmitContainerTokenRequest#containerConfig} object.
     * 
     * @param req
     *      The container request as submitted by the user
     * @return
     *      A new instance of {@link SubmitContainerTokenRequest} having the container file modified
     *      to include the changes defined by the {@link SubmitContainerTokenRequest#containerConfig} object
     *      or the original request object if the request does provide an empty config object 
     */
    SubmitContainerTokenRequest freezeBuildRequest(final SubmitContainerTokenRequest req, final User user)

}
