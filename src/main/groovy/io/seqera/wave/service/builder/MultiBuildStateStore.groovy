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
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.configuration.BuildEnabled
import io.seqera.serde.moshi.MoshiEncodeStrategy
import io.seqera.data.store.state.AbstractStateStore
import io.seqera.data.store.state.impl.StateProvider
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implement a {@link io.seqera.data.store.state.StateStore} for {@link MultiBuildEntry} objects
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@Requires(bean = BuildEnabled)
@CompileStatic
class MultiBuildStateStore extends AbstractStateStore<MultiBuildEntry> {

    @Inject
    private BuildConfig config

    MultiBuildStateStore(StateProvider<String,String> provider) {
        super(provider, new MoshiEncodeStrategy<MultiBuildEntry>() {})
    }

    @Override
    protected String getPrefix() {
        return 'wave-multibuild/v1'
    }

    @Override
    protected Duration getDuration() {
        return config.statusDuration
    }
}
