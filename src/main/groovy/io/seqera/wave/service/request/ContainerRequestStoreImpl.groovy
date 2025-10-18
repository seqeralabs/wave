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

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.configuration.ContainerRequestConfig
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.store.state.AbstractStateStore
import io.seqera.wave.store.state.impl.StateProvider
import jakarta.inject.Singleton
/**
 * Implements a cache store for {@link ContainerRequest}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class ContainerRequestStoreImpl extends AbstractStateStore<ContainerRequest> implements ContainerRequestStore {

    private ContainerRequestConfig config

    ContainerRequestStoreImpl(StateProvider<String, String> delegate, ContainerRequestConfig config) {
        super(delegate, new MoshiEncodeStrategy<ContainerRequest>(){})
        this.config = config
        log.info "Creating Tokens cache store ― duration=${config.cache.duration}"
    }

    @Override
    protected String getPrefix() {
        return 'wave-tokens/v1'
    }

    @Override
    protected Duration getDuration() {
        return config.cache.duration
    }

    @Override
    ContainerRequest get(String key) {
        return (ContainerRequest) super.get(key)
    }

    @Override
    void put(String key, ContainerRequest value) {
        super.put(key, value)
    }

    @Override
    void put(String key, ContainerRequest value, Duration ttl) {
        super.put(key, value, ttl)
    }

    @Override
    void remove(String key) {
        super.remove(key)
    }

}
