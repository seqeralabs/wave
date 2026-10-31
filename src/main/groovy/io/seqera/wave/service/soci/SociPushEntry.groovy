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

    enum Status {
        /** waiting for the container build or mirror */
        WAITING_CONTAINER,
        /** the container layers are indexed, then the push job runs */
        PUBLISHING,
        SUCCEEDED,
        FAILED,
        /** the container has no layer to index, there is nothing to publish */
        NONE
    }

    final SociPushRequest request
    final Status status
    final String error
    /** the digest of the target image content the SOCI tag is published from */
    final String sourceDigest

    protected SociPushEntry() {}

    SociPushEntry(SociPushRequest request, Status status, String error, String sourceDigest=null) {
        this.request = request
        this.status = status
        this.error = error
        this.sourceDigest = sourceDigest
    }

    @Override
    String getKey() {
        return request.sociImage
    }

    @Override
    boolean done() {
        return status==Status.SUCCEEDED || status==Status.FAILED || status==Status.NONE
    }

    boolean succeeded() {
        return status==Status.SUCCEEDED
    }

    SociPushEntry withStatus(Status status) {
        return new SociPushEntry(request, status, null, sourceDigest)
    }

    SociPushEntry withSourceDigest(String digest) {
        return new SociPushEntry(request, status, null, digest)
    }

    SociPushEntry failed(String error) {
        return new SociPushEntry(request, Status.FAILED, error, sourceDigest)
    }

    static SociPushEntry of(SociPushRequest request, Status status) {
        return new SociPushEntry(request, status, null)
    }
}
