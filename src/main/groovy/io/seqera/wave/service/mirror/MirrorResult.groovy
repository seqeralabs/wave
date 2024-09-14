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

import java.time.Duration
import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import jakarta.inject.Singleton
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includeNames = true, includePackage = false)
@Singleton
@CompileStatic
@Canonical
class MirrorResult {
    enum Status { PENDING, COMPLETE }

    final String id
    final String sourceImage
    final String targetImage
    final Instant creationTime
    final Status status
    final Duration duration
    final Integer exitCode
    final String logs

    boolean done() {
        status==Status.COMPLETE
    }

    boolean succeeded() {
        status==Status.COMPLETE && exitCode==0
    }

    MirrorResult complete( Integer exitCode, String logs) {
        new MirrorResult(
                this.id,
                this.sourceImage,
                this.targetImage,
                this.creationTime,
                Status.COMPLETE,
                Duration.between(this.creationTime, Instant.now()),
                exitCode
        )
    }

    static MirrorResult from(MirrorRequest request) {
        new MirrorResult(
                request.id,
                request.sourceImage,
                request.targetImage,
                request.creationTime,
                Status.PENDING
        )
    }
}
