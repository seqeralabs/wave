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

package io.seqera.wave.memstore.range


import groovy.transform.CompileStatic
import io.seqera.wave.memstore.range.impl.RangeProvider
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
abstract class AbstractRangeStore implements RangeStore {

    private RangeProvider delegate

    protected abstract String getKey()

    AbstractRangeStore(RangeProvider provider) {
        this.delegate = provider
    }

    @Override
    void add(String name, double  score) {
        delegate.add(getKey(), name, score)
    }

    @Override
    List<String> getRange(double min, double max, int count) {
        return getRange(min, max, count, true)
    }

    List<String> getRange(double min, double max, int count, boolean remove) {
        return delegate.getRange(getKey(), min, max, count, remove) ?: List.<String>of()
    }

}
