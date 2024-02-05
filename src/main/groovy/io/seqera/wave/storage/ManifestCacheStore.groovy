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

package io.seqera.wave.storage

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider
import io.seqera.wave.storage.reader.ContentReader
import io.seqera.wave.storage.reader.LayerContentReader
import jakarta.inject.Singleton
/**
 * Implements manifest cache for {@link DigestStore}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class ManifestCacheStore extends AbstractCacheStore<DigestStore> implements Storage {

    final private Duration duration

    ManifestCacheStore(
            CacheProvider<String, String> provider,
            @Value('${wave.storage.cache.duration:`1h`}') Duration duration)
    {
        super(provider, new MoshiEncodeStrategy<DigestStore>() {})
        this.duration = duration
        log.info "Creating Manifests cache store ― duration=$duration"
    }

    @Override
    protected String getPrefix() {
        return "wave-blobs/v1:"
    }

    @Override
    protected Duration getDuration() {
        return duration
    }

    @Override
    Optional<DigestStore> getManifest(String path) {
        log.trace "Get manifest ==> $path"
        final result = this.get(path)
        result!=null ? Optional.of(result) : Optional.<DigestStore>empty()
    }

    @Override
    DigestStore saveManifest(String path, String manifest, String type, String digest) {
        log.trace "Save Manifest [size: ${manifest.size()}] ==> $path"
        final bytes = manifest.getBytes()
        final result = new ZippedDigestStore(bytes, type, digest, bytes.length);
        this.put(path, result)
        return result;
    }

    DigestStore saveManifest(String path, DigestStore store) {
        log.trace "Save Manifest [store] ==> $path"
        this.put(path, store)
        return store;
    }

    @Override
    Optional<DigestStore> getBlob(String path) {
        log.trace "Get Blob ==> $path"
        final result = this.get(path)
        result!=null ? Optional.of(result) : Optional.<DigestStore>empty()
    }

    @Override
    DigestStore saveBlob(String path, byte[] content, String type, String digest) {
        log.trace "Save Blob [size: ${content.size()}] ==> $path"
        final result = new ZippedDigestStore(content, type, digest, content.length)
        this.put(path, result)
        return result
    }

    @Override
    DigestStore saveBlob(String path, ContentReader content, String type, String digest, int size) {
        log.trace "Save Blob ==> $path"
        final result = content instanceof LayerContentReader
                ? new LayerDigestStore(content.location, type, digest, size)
                : new LazyDigestStore(content, type, digest, size)
        this.put(path, result)
        return result
    }


}
