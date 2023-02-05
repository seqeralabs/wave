package io.seqera.wave.service.token

import java.time.Instant

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.configuration.TokenConfig
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.util.LongRndKey
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Service to fulfill request for an augmented container
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Singleton
class ContainerTokenServiceImpl implements ContainerTokenService {

    @Inject
    private ContainerTokenStore containerTokenStorage

    @Inject
    private TokenConfig config

    @Override
    TokenData computeToken(ContainerRequestData request) {
        final token = LongRndKey.rndHex()
        final expiration = Instant.now().plus(config.cache.duration);
        containerTokenStorage.put(token, request)
        return new TokenData(token, expiration)
    }

    @Override
    ContainerRequestData getRequest(String token) {
        return containerTokenStorage.get(token)
    }
}
