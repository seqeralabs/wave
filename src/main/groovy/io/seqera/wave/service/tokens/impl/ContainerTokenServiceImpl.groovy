package io.seqera.wave.service.tokens.impl


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.tokens.ContainerTokenService
import io.seqera.wave.service.tokens.ContainerTokenStorage
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

    @Inject ContainerTokenStorage containerTokenStorage

    @Override
    String computeToken(ContainerRequestData request) {
        final token = LongRndKey.rndHex()
        containerTokenStorage.put(token, request)
        return token
    }

    @Override
    ContainerRequestData getRequest(String token) {
        return containerTokenStorage.get(token)
    }
}
