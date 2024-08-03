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

package io.seqera.wave.auth

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider
import jakarta.inject.Singleton
/**
 * Implement a cache store for {@link io.seqera.wave.auth.RegistryAuthServiceImpl.CacheKey} object and token that
 * can be distributed across wave replicas
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class RegistryTokenCacheStore extends AbstractCacheStore<String> {

    RegistryTokenCacheStore(CacheProvider<String, String> provider) {
        super(provider, new MoshiEncodeStrategy<String>() {})
        log.info "Creating Registry Auth token cache store"
    }

    @Override
    protected String getPrefix() {
        return 'registry-token/v1:'
    }

    @Override
    protected Duration getDuration() {
        return RegistryAuthServiceImpl._1_HOUR
    }
}
