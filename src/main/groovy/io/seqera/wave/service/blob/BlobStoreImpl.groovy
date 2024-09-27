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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.store.state.AbstractStateStore
import io.seqera.wave.store.state.impl.StateProvider
import jakarta.inject.Inject
/**
 * Implement a distributed store for blob cache entry.
 *
 * NOTE: This only stores blob caching *metadata* i.e. {@link BlobEntry}.
 * The blob binary is stored into an object storage bucket
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class BlobStoreImpl extends AbstractStateStore<BlobEntry> implements BlobStateStore {

    @Inject
    private BlobCacheConfig blobConfig

    BlobStoreImpl(StateProvider<String, String> provider) {
        super(provider, new MoshiEncodeStrategy<BlobEntry>() {})
    }

    @Override
    protected String getPrefix() {
        return 'wave-blobcache/v1:'
    }

    @Override
    protected Duration getDuration() {
        return blobConfig.statusDuration
    }

    @Override
    Duration getTimeout() {
        return blobConfig.transferTimeout
    }

    @Override
    Duration getDelay() {
        return blobConfig.statusDelay
    }

    @Override
    BlobEntry getBlob(String key) {
        return get(key)
    }

    @Override
    boolean storeIfAbsent(String key, BlobEntry info) {
        return putIfAbsent(key, info)
    }

    @Override
    void storeBlob(String key, BlobEntry info) {
        final ttl = info.state == BlobEntry.State.ERRORED
                ? blobConfig.failureDuration
                : blobConfig.statusDuration
        put(key, info, ttl)
    }

}
