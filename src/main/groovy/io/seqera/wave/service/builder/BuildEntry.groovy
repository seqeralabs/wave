/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.seqera.wave.service.job.JobEntry
import io.seqera.wave.store.state.StateRecord
/**
 * Class to store build request and result in cache
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@ToString(includePackage = false, includeNames = true)
@EqualsAndHashCode
@CompileStatic
class BuildEntry implements JobEntry, StateRecord {

    final BuildRequest request

    final BuildResult result

    @Override
    String getRecordId() {
        if( !request.buildId )
            throw new IllegalStateException("Missing build id")
        return request.buildId
    }

    @Override
    boolean done() {
        return result.done()
    }

    protected BuildEntry() {}

    BuildEntry(BuildRequest request, BuildResult result) {
        this.request = request
        this.result = result
    }

    BuildEntry withResult(BuildResult result) {
        new BuildEntry(request, result)
    }

}
