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

package io.seqera.wave.service.scan

import java.time.Duration

import groovy.transform.CompileStatic
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.store.state.AbstractStateStore
import io.seqera.wave.store.state.impl.StateProvider
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Implements a store strategy for scan ids
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class ScanIdStore extends AbstractStateStore<ScanId> {

    @Inject
    private ScanConfig config

    ScanIdStore(StateProvider<String, String> provider) {
        super(provider, new MoshiEncodeStrategy<ScanId>() {})
    }

    @Override
    protected String getPrefix() {
        return 'wave-scanid/v1'
    }

    @Override
    protected Duration getDuration() {
        return config.scanIdDuration
    }
}
