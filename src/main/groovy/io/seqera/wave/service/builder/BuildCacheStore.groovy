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

package io.seqera.wave.service.builder

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.configuration.BuildConfig
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

    private BuildConfig buildConfig

    BuildCacheStore(CacheProvider<String, String> provider, BuildConfig buildConfig ) {
        super(provider, new MoshiEncodeStrategy<BuildResult>() {})
        this.buildConfig = buildConfig
    }

    @Override
    protected String getPrefix() {
        return 'wave-build/v1:'
    }

    @Override
    protected Duration getDuration() {
        return buildConfig.statusDuration
    }

    @Override
    Duration getTimeout() {
        return buildConfig.buildTimeout
    }

    @Override
    Duration getDelay() {
        return buildConfig.statusDelay
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
