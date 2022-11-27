package io.seqera.wave.service.token

import io.seqera.wave.service.ContainerRequestData

/**
 * Define the container request token persistence operations
 * 
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
interface ContainerTokenStore {

    void put(String key, ContainerRequestData request)

    ContainerRequestData get(String key)
}
