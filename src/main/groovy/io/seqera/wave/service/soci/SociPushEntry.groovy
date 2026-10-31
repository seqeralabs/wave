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
 * Model the state of a SOCI-enabled tag publication
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includeNames = true, includePackage = false)
@EqualsAndHashCode
@CompileStatic
class SociPushEntry implements StateEntry<String>, JobEntry {

    final SociPushRequest request
    final boolean completed
    final Integer exitCode
    final String error

    protected SociPushEntry() {}

    SociPushEntry(SociPushRequest request, boolean completed, Integer exitCode, String error) {
        this.request = request
        this.completed = completed
        this.exitCode = exitCode
        this.error = error
    }

    @Override
    String getKey() {
        return request.sociImage
    }

    @Override
    boolean done() {
        return completed
    }

    boolean succeeded() {
        return completed && exitCode==0 && error==null
    }

    SociPushEntry complete(Integer exitCode, String error) {
        return new SociPushEntry(request, true, exitCode, error)
    }

    static SociPushEntry of(SociPushRequest request) {
        return new SociPushEntry(request, false, null, null)
    }
}
