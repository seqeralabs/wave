/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.service.soci

import java.time.Duration

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.seqera.data.store.state.AbstractStateStore
import io.seqera.data.store.state.impl.StateProvider
import io.seqera.serde.moshi.MoshiEncodeStrategy
import io.seqera.wave.configuration.SociConfig
import io.seqera.wave.configuration.SociEnabled
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Store the SOCI publications waiting for each layer, by layer digest
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@Requires(bean = SociEnabled)
@CompileStatic
class SociWaitersStore extends AbstractStateStore<SociWaiters> {

    @Inject
    private SociConfig config

    SociWaitersStore(StateProvider<String,String> provider) {
        super(provider, new MoshiEncodeStrategy<SociWaiters>() {})
    }

    @Override
    protected String getPrefix() {
        return 'wave-soci-waiters/v1'
    }

    @Override
    protected Duration getDuration() {
        return config.publishTimeout
    }

    /**
     * Add a waiter to a layer. Note: a read-modify-write, so a concurrent addition may be lost; the publication
     * re-checks the zTOCs after registering, and its state expires after the publish timeout
     */
    void add(String layerDigest, String sociImage) {
        final current = get(layerDigest)?.sociImages ?: List.<String>of()
        if( current.contains(sociImage) )
            return
        put(layerDigest, new SociWaiters(current + sociImage))
    }
}
