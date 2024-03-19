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

package io.seqera.wave.service.token

import io.seqera.wave.service.ContainerRequestData

/**
 * service to fulfill request for an augmented container
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface ContainerTokenService {

    /**
     * Get (generate) a new container token for the specified container request data
     *
     * @param request An instance of {@link io.seqera.wave.service.ContainerRequestData}
     * @return A new token string that's used to track this request
     */
    TokenData computeToken(ContainerRequestData request)

    /**
     * Get the container image for the given container token
     *
     * @param token A container token string
     * @return the corresponding token string, or null if the token is unknown
     */
    ContainerRequestData getRequest(String token)

    /**
     * Evict the container request entry from the cache for the given container token
     *
     * @param token A container token string
     * @return the corresponding token string, or null if the token is unknown
     */
    ContainerRequestData evictRequest(String token)

}
