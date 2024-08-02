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

package io.seqera.wave.auth.cache

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.auth.RegistryAuth
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider
import jakarta.inject.Singleton

/**
 * Implement a cache store for {@link RegistryAuth} object that
 * can be distributed across wave replicas
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class RegistryAuthCacheStore extends AbstractCacheStore<RegistryAuth> {

    private Duration duration

    RegistryAuthCacheStore(
            CacheProvider<String, String> provider,
            @Value('${wave.registry-auth.cache.duration:`3h`}') Duration duration)
    {
        super(provider, new MoshiEncodeStrategy<RegistryAuth>() {})
        this.duration = duration
        log.info "Creating Registry Auth cache store ― duration=$duration"
    }

    @Override
    protected String getPrefix() {
        return 'registry-auth/v1:'
    }

    @Override
    protected Duration getDuration() {
        return duration
    }
}
