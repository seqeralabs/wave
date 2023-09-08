/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

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
