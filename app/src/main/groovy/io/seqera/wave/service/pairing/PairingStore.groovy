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

package io.seqera.wave.service.pairing

import java.time.Duration
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.store.state.AbstractStateStore
import io.seqera.wave.store.state.impl.StateProvider
import jakarta.inject.Singleton
/**
 * Implements a cache store for {@link PairingRecord}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class PairingStore extends AbstractStateStore<PairingRecord> {

    /**
     * How long the pairing key can stay in the cache store before is evicted
     */
    @Value('${wave.pairing-key.duration:`30d`}')
    private Duration duration

    /**
     * The period of time after which the token should be renewed
     */
    @Value('${wave.pairing-key.lease:`1d`}')
    private Duration lease

    PairingStore(StateProvider<String, String> provider) {
        super(provider, new MoshiEncodeStrategy<PairingRecord>() {})
    }

    @PostConstruct
    private void init() {
        log.info "Creating Pairing cache store ― duration=${duration}; lease=${lease}"
    }

    @Override
    protected String getPrefix() {
        return 'pairing-keys/v1'
    }

    /**
     * @return A duration representing the TTL of the entries in the cache
     */
    @Override
    protected Duration getDuration() {
        // note: the cache store should be modified to allow the support for
        // infinite duration using with null
        return duration
    }
}
