package io.seqera.wave.service

import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.tower.User

/**
 * Defines the operations to handle Wave containers requests
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface ContainerRequestService {

    ContainerRequestData makeRequestData(SubmitContainerTokenRequest req, User user, String ip)

}

