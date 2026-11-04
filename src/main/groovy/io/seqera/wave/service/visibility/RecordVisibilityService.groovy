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

package io.seqera.wave.service.visibility

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.mirror.ContainerMirrorService
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.request.ContainerRequestService
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Decides whether a record's metadata may be rendered by Wave's own view pages.
 *
 * The answer comes from the {@code isPublic} flag the caller supplied on the submit request.
 * Absent means public: the field is optional, older clients never send it, and nothing that works
 * today may stop working because a field was added.
 *
 * This governs rendering only. It does not restrict the read API, the registry pull path, or how
 * containers are built, scanned and mirrored.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
@Singleton
class RecordVisibilityService {

    @Inject
    @Nullable
    private ContainerBuildService buildService

    @Inject
    @Nullable
    private ContainerMirrorService mirrorService

    @Inject
    @Nullable
    private ContainerRequestService containerService

    /**
     * Null means public — see the class note on why the default cannot be the other way round.
     * Callers pass the flag off the record, e.g. {@code isPublic(record?.isPublic)}, so one method
     * serves every record type that carries the field.
     */
    boolean isPublic(@Nullable Boolean isPublic) {
        return isPublic == null || isPublic
    }

    /**
     * A scan carries no visibility of its own — it is derived from a build, mirror or container
     * request and takes that record's answer.
     *
     * A scan with no resolvable parent is public. That is the case for a scan submitted against an
     * existing image through {@code POST /view/scans}: nobody declared it private, and it exposes
     * vulnerability data for an image the submitter already had to name.
     */
    boolean isScanPublic(@Nullable WaveScanRecord record) {
        if( record == null )
            return true
        // A scan can name more than one parent: a container request that triggered a build carries
        // both its requestId and that buildId. The build record is shared between every caller that
        // asked for the same image, while the request record belongs to one submitter, so taking
        // whichever is checked first lets one override the other in both directions. Withhold when
        // any parent that resolves is private; an unresolvable one keeps its silence.
        if( record.buildId && !isPublic(buildService?.getBuildRecord(record.buildId)?.isPublic) )
            return false
        if( record.mirrorId && !isPublic(mirrorService?.getMirrorResult(record.mirrorId)?.isPublic) )
            return false
        if( record.requestId && !isPublic(containerService?.loadContainerRecord(record.requestId)?.isPublic) )
            return false
        return true
    }
}
