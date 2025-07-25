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
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.configuration.ScanEnabled
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.store.state.AbstractStateStore
import io.seqera.wave.store.state.impl.StateProvider
import jakarta.inject.Singleton
/**
 * Implement a store for scan state
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(bean = ScanEnabled)
@Singleton
@CompileStatic
class ScanStateStore extends AbstractStateStore<ScanEntry> {

    private ScanConfig config

    ScanStateStore(StateProvider<String, String> provider, ScanConfig config) {
        super(provider, new MoshiEncodeStrategy<ScanEntry>() { })
        this.config = config
    }

    @Override
    protected String getPrefix() {
        return 'wave-scan/v1'
    }

    @Override
    protected Duration getDuration() {
        return config.statusDuration
    }

    ScanEntry getScan(String key) {
        super.get(key)
    }

    void storeScan(ScanEntry entry) {
        super.put(entry.key, entry)
    }
}
