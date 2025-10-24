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

import java.time.Duration
/**
 * Implement a distributed store for blob cache entry.
 *
 * NOTE: This only stores blob caching *metadata* i.e. {@link BlobEntry}.
 * The blob binary is stored into an object storage bucket 
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface BlobStateStore {

    /**
     * @return The max amount of time allowed to transfer the blob binary in the cache storage
     */
    Duration getTimeout()

    /**
     * @return The time interval every when the status of the blob transfer is checked
     */
    Duration getDelay()

    /**
     * Retrieve the blob cache info object for the given key
     *
     * @param key The unique key associate with the {@link BlobEntry} object
     * @return The {z@link BlobCacheInfo} object associated with the specified key, or {@code null} otherwise
     */
    BlobEntry getBlob(String key)

    /**
     * Store the blob cache info object with the specified key
     *
     * @param key The unique to be used to store the blob cache info
     * @param info The {@link BlobEntry} object modelling the container blob information
     */
    void storeBlob(String key, BlobEntry info)

    /**
     * Store a blob cache location only if the specified key does not exit
     *
     * @param key The key of the blob
     * @param info The {@link BlobEntry} holding the blob location information
     * @return {@code true} if the {@link BlobEntry} was stored, {@code false} otherwise
     */
    boolean storeIfAbsent(String key, BlobEntry info)

}
