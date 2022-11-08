package io.seqera.wave.service.token

import io.seqera.wave.service.ContainerRequestData

/**
 * Define the container request token persistence operations
 * 
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
interface ContainerTokenStore {

    ContainerRequestData putRequest(String key, ContainerRequestData request)

    ContainerRequestData getRequest(String key)
}
