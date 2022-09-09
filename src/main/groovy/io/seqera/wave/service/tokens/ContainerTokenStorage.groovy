package io.seqera.wave.service.tokens

import io.seqera.wave.service.ContainerRequestData


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
interface ContainerTokenStorage {

    ContainerRequestData put(String key, ContainerRequestData request)

    ContainerRequestData get(String key)
}
