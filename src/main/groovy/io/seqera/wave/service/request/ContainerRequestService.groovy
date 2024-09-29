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

import io.seqera.wave.service.persistence.WaveContainerRecord

/**
 * service to fulfill request for an augmented container
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface ContainerRequestService {

    /**
     * Get (generate) a new container token for the specified container request data
     *
     * @param request
     *      An instance of {@link ContainerRequest}
     * @return
     *      The {@link TokenData} representing the unique token and the expiration time
     */
    TokenData computeToken(ContainerRequest request)

    /**
     * Get the container image for the given container requestId
     *
     * @param requestId The
     *      container request unique id
     * @return
     *      The {@link ContainerRequest} object for the specified id,
     *      or {@code null} if the requestId is unknown
     */
    ContainerRequest getRequest(String requestId)

    /**
     * Evict the container request entry from the cache for the given container request id
     *
     * @param requestId
     *      The id of the request to be evicted
     * @return
     *      The corresponding token string, or null if the token is unknown
     */
    ContainerRequest evictRequest(String requestId)

    /**
     * Load the record persisted in the requests db
     *
     * @param requestId
     *      The unique id of the request to be loaded
     * @return
     *      The {@link WaveContainerRecord} object corresponding the specified id or {@code null} otherwise
     */
    WaveContainerRecord loadContainerRecord(String requestId)

}
