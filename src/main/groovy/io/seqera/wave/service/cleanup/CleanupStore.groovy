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

package io.seqera.wave.service.cleanup

import groovy.transform.CompileStatic
import io.seqera.wave.memstore.range.AbstractRangeStore
import io.seqera.wave.memstore.range.impl.RangeProvider
import jakarta.inject.Singleton

/**
 * Implements a timed range to store and retrieve IDs
 * of resources to be cleaned up.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class CleanupStore extends AbstractRangeStore {

    CleanupStore(RangeProvider provider) {
        super(provider)
    }

    @Override
    protected String getKey() {
        return 'cleanup-store/v1:'
    }
}
