/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2025, Seqera Labs
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

package io.seqera.wave.service.persistence.migrate.cache

import java.time.Duration

import groovy.transform.CompileStatic
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.store.state.AbstractStateStore
import io.seqera.wave.store.state.impl.StateProvider
import jakarta.inject.Singleton
/**
 * Cache for data migration entries
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Singleton
@CompileStatic
class DataMigrateCache extends AbstractStateStore<DataMigrateEntry> {

    DataMigrateCache(StateProvider<String,String> provider) {
        super(provider, new MoshiEncodeStrategy<DataMigrateEntry>() {})
    }

    @Override
    protected String getPrefix() {
        return 'migrate-surreal/v1'
    }

    @Override
    protected Duration getDuration() {
        return Duration.ofDays(30)
    }
}
