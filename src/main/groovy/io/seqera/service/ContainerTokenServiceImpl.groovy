package io.seqera.service

import io.seqera.util.LongRndKey
import jakarta.inject.Singleton
/**
 * Service to fulfill request for an augmented container
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
class ContainerTokenServiceImpl implements ContainerTokenService {

    private Map<String, ContainerRequestData> cache = new HashMap<>()

    @Override
    String getToken(ContainerRequestData request) {
        final token = LongRndKey.rndHex()
        cache.put(token, request)
        return token
    }

    @Override
    ContainerRequestData getRequest(String token) {
        return cache.get(token)
    }
}
