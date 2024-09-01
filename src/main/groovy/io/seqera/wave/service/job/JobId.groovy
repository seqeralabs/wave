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

package io.seqera.wave.service.job

import java.time.Duration
import java.time.Instant

import com.google.common.hash.Hashing
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.seqera.wave.service.builder.BuildRequest

/**
 * Model a unique job to be managed
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Canonical
class JobId {
    enum Type { Transfer, Build, Scan }

    final Type type
    final String id
    final Instant creationTime
    final Duration maxDuration
    final String schedulerId
    final Map<String,Object> context

    JobId( Type type, String id, Instant creationTime, Duration maxDuration ) {
        this.type = type
        this.id = id
        this.creationTime = creationTime
        this.maxDuration = maxDuration
        this.schedulerId = generate(type, id, creationTime)
        this.context = Map.of()
    }

    JobId( Type type, String id, Instant creationTime, Duration maxDuration, String operationId, Map<String,Object> context) {
        this.type = type
        this.id = id
        this.creationTime = creationTime
        this.maxDuration = maxDuration
        this.schedulerId = operationId
        this.context = context
    }

    static JobId transfer(String id, Duration maxDuration) {
        new JobId(Type.Transfer, id, Instant.now(), maxDuration)
    }

    static JobId build(BuildRequest request) {
        new JobId(
                Type.Build,
                request.targetImage,
                request.startTime,
                request.maxDuration,
                "build-${request.buildId}".toString(),
                [buildId:request.buildId, identity:request.identity]
        )
    }

    static JobId scan(String id, Duration maxDuration) {
        new JobId(Type.Scan, id, Instant.now(), maxDuration)
    }

    static private String generate(Type type, String id, Instant creationTime) {
        final prefix = type.toString().toLowerCase()
        return prefix + '-' + Hashing
                .sipHash24()
                .newHasher()
                .putUnencodedChars(id)
                .putUnencodedChars(type.toString())
                .putUnencodedChars(creationTime.toString())
                .hash()
    }

}
