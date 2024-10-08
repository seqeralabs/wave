/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

package io.seqera.wave.service.request

import java.time.Instant

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.configuration.TokenConfig
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveContainerRecord
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
class ContainerRequestServiceImpl implements ContainerRequestService {

    @Inject
    private ContainerRequestStore containerTokenStorage

    @Inject
    private TokenConfig config

    @Inject
    private ContainerRequestStoreImpl tokenCache

    @Inject
    private PersistenceService persistenceService

    @Override
    TokenData computeToken(ContainerRequest request) {
        final expiration = Instant.now().plus(config.cache.duration);
        containerTokenStorage.put(request.requestId, request)
        return new TokenData(request.requestId, expiration)
    }

    @Override
    ContainerRequest getRequest(String requestId) {
        return containerTokenStorage.get(requestId)
    }

    @Override
    ContainerRequest evictRequest(String requestId) {
        if(!requestId)
            return null

        final request = tokenCache.get(requestId)
        if( request ) {
            tokenCache.remove(requestId)
        }
        return request
    }

    WaveContainerRecord loadContainerRecord(String requestId) {
        persistenceService.loadContainerRequest(requestId)
    }
}
