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
 * Cache the zTOC available for each layer. The source of truth is the zTOC repository, see {@link SociIndexServiceImpl}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@Requires(bean = SociEnabled)
@CompileStatic
class SociZtocStore extends AbstractStateStore<ZtocEntry> {

    @Inject
    private SociConfig config

    SociZtocStore(StateProvider<String,String> provider) {
        super(provider, new MoshiEncodeStrategy<ZtocEntry>() {})
    }

    @Override
    protected String getPrefix() {
        return 'wave-soci-ztoc/v1'
    }

    @Override
    protected Duration getDuration() {
        return config.ztocCacheDuration
    }
}
