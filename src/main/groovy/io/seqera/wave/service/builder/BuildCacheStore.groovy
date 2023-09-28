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

package io.seqera.wave.service.builder

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider
import jakarta.inject.Singleton
/**
 * Implements Cache store for {@link BuildResult}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class BuildCacheStore extends AbstractCacheStore<BuildResult> implements BuildStore {

    private Duration duration
    private Duration delay
    private Duration timeout

    BuildCacheStore(
            CacheProvider<String, String> provider,
            @Value('${wave.build.status.delay}') Duration delay,
            @Value('${wave.build.timeout}') Duration timeout,
            @Value('${wave.build.status.duration}') Duration duration
    ) {
        super(provider, new MoshiEncodeStrategy<BuildResult>() {})
        this.duration = duration
        this.delay = delay
        this.timeout = timeout
        log.info "Creating Build cache store ― duration=$duration; delay=$delay; timeout=$timeout"
    }

    @Override
    protected String getPrefix() {
        return 'wave-build/v1:'
    }

    @Override
    protected Duration getDuration() {
        return duration
    }

    @Override
    Duration getTimeout() {
        return timeout
    }

    @Override
    Duration getDelay() {
        return delay
    }

    @Override
    BuildResult getBuild(String imageName) {
        return get(imageName)
    }

    @Override
    void storeBuild(String imageName, BuildResult result) {
        put(imageName, result)
    }

    @Override
    void storeBuild(String imageName, BuildResult result, Duration ttl) {
        put(imageName, result, ttl)
    }

    @Override
    boolean storeIfAbsent(String imageName, BuildResult build) {
        return putIfAbsent(imageName, build)
    }

    @Override
    void removeBuild(String imageName) {
        remove(imageName)
    }

}
