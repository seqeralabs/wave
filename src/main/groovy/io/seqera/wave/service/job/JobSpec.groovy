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

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.seqera.wave.util.LongRndKey

/**
 * Model a unique job to be managed
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@EqualsAndHashCode
@ToString(includePackage = false, includeNames = true)
@CompileStatic
class JobSpec {

    enum Type { Transfer, Build, Scan }

    /**
     * The job unique identifier
     */
    final String id

    /**
     * The type of the job. See {@link Type}
     */
    final Type type

    /**
     * The unique id of the state record associated with this job
     */
    final String recordId

    /**
     * The unique name of the underlying infra operation associated with this job
     * e.g. the K8s job name or Docker container name
     */
    final String operationName

    /**
     * The instant when this job was created
     */
    final Instant creationTime

    /**
     * The max time to live of the job
     */
    final Duration maxDuration

    /**
     * The temporary path associated with this job (optional). This is expected to be deleted
     * once the job execution terminates.
     */
    final Path workDir

    protected JobSpec(String id, Type type, String recordId, String operationName, Instant createdAt, Duration maxDuration, Path dir) {
        this.id = id
        this.type = type
        this.recordId = recordId
        this.operationName = operationName
        this.maxDuration = maxDuration
        this.creationTime = createdAt
        this.workDir = dir
    }

    static JobSpec transfer(String stateId, String operationName, Instant creationTime, Duration maxDuration) {
        new JobSpec(
                LongRndKey.rndHex(),
                Type.Transfer,
                stateId,
                operationName,
                creationTime,
                maxDuration,
                null
        )
    }

    static JobSpec scan(String stateId, String operationName, Instant creationTime, Duration maxDuration, Path dir) {
        new JobSpec(
                LongRndKey.rndHex(),
                Type.Scan,
                stateId,
                operationName,
                creationTime,
                maxDuration,
                dir
        )
    }

    static JobSpec build(String stateId, String operationName, Instant creationTime, Duration maxDuration,  Path dir) {
        new JobSpec(
                LongRndKey.rndHex(),
                Type.Build,
                stateId,
                operationName,
                creationTime,
                maxDuration,
                dir
        )
    }
}
