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

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.seqera.data.store.state.StateEntry
import io.seqera.wave.service.job.JobEntry
/**
 * Model the state of a zTOC indexing job. A succeeded entry caches the layer zTOC
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includeNames = true, includePackage = false)
@EqualsAndHashCode
@CompileStatic
class SociIndexEntry implements StateEntry<String>, JobEntry {

    /** The indexing request, {@code null} when the zTOC was found in the zTOC repository */
    final SociIndexRequest request
    /** The zTOC of the layer, {@code null} while pending or on failure */
    final ZtocEntry ztoc
    final boolean completed
    final Integer exitCode
    final String error

    protected SociIndexEntry() {}

    SociIndexEntry(SociIndexRequest request, ZtocEntry ztoc, boolean completed, Integer exitCode, String error) {
        this.request = request
        this.ztoc = ztoc
        this.completed = completed
        this.exitCode = exitCode
        this.error = error
    }

    @Override
    String getKey() {
        return request ? request.key : SociIndexRequest.key(ztoc.layerDigest, ztoc.spanSize)
    }

    @Override
    boolean done() {
        return completed
    }

    boolean succeeded() {
        return completed && exitCode==0 && error==null
    }

    SociIndexEntry complete(Integer exitCode, ZtocEntry ztoc, String error) {
        return new SociIndexEntry(request, ztoc, true, exitCode, error)
    }

    static SociIndexEntry of(SociIndexRequest request) {
        return new SociIndexEntry(request, null, false, null, null)
    }

    /**
     * @return A succeeded entry for a zTOC found in the zTOC repository
     */
    static SociIndexEntry found(ZtocEntry ztoc) {
        return new SociIndexEntry(null, ztoc, true, 0, null)
    }
}
