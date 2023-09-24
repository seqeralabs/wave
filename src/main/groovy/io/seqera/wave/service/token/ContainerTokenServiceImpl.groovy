/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
