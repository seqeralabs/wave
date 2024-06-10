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

package io.seqera.wave.service.counter

import groovy.transform.CompileStatic
import io.seqera.wave.service.counter.impl.CounterProvider

/**
 * Generic counters service
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
abstract class AbstractCounterStore implements CounterStore {

    private CounterProvider provider

    protected abstract String getPrefix()

    AbstractCounterStore(CounterProvider provider) {
        this.provider = provider
    }

    @Override
    long inc(String key, long value) {
        provider.inc(getPrefix(), key, value)
    }

    long inc(String key) {
        provider.inc(getPrefix(), key, 1)
    }

    @Override
    Long get(String key) {
        provider.get(getPrefix(), key)
    }

    @Override
    Map<String, Long> getAllMatchingEntries(String pattern) {
        provider.getAllMatchingEntries(getPrefix(), pattern)
    }
}
