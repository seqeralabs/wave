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

package io.seqera.wave.service.blob

import javax.annotation.concurrent.ThreadSafe

import io.seqera.wave.core.RoutePath
/**
 * Defines a caching service for container layer blob object.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ThreadSafe
interface BlobCacheService {

    /**
     * Store a container blob into the cache storage that allows fast retrieval
     * via HTTP content delivery network, and returns a {@link BlobCacheInfo} object
     * holding the HTTP download URI.
     *
     * Note this method is thread safe is expected to be thread safe across multiple replicas.
     *
     * When two cache requests are submitted nearly at the same time, the first request carries out
     * the storing in the cache operation. The second request blobs awaiting for the storing in the
     * cache to be completed and eventually returns the same {@link BlobCacheInfo} holding the cache
     * information.
     *
     * @param route The HTTP request of a container layer blob
     * @param headers The HTTP headers of a container layer blob
     * @return
     */
    BlobCacheInfo retrieveBlobCache(RoutePath route, Map<String,List<String>> headers)

}
