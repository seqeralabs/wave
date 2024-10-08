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

package io.seqera.wave.service.mirror

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.seqera.wave.service.job.JobEntry
import io.seqera.wave.store.state.RequestIdAware
import io.seqera.wave.store.state.StateEntry
/**
 * Model a container mirror entry object
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includeNames = true, includePackage = false)
@EqualsAndHashCode
@CompileStatic
class MirrorEntry implements StateEntry<String>, JobEntry, RequestIdAware {

    final MirrorRequest request

    final MirrorResult result

    protected MirrorEntry() {}

    MirrorEntry(MirrorRequest request, MirrorResult result) {
        this.request = request
        this.result = result
    }

    @Override
    String getKey() {
        return request.targetImage
    }

    @Override
    String getRequestId() {
        return request.mirrorId
    }

    @Override
    boolean done() {
        result?.status==MirrorResult.Status.COMPLETED
    }

    /**
     * Create a {@link MirrorEntry} object with the current {@link MirrorRequest} and
     * the specified {@link MirrorResult} object
     *
     * @param result The {@link MirrorResult} object to be use as result
     * @return The new {@link MirrorEntry} instance
     */
    MirrorEntry withResult(MirrorResult result) {
        new MirrorEntry(this.request, result)
    }

    /**
     * Create a {@link MirrorEntry} object with the given {@link MirrorRequest} object
     */
    static MirrorEntry of(MirrorRequest request) {
        new MirrorEntry(request, MirrorResult.of(request))
    }

}
