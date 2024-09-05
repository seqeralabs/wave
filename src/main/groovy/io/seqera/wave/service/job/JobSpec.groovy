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

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString

/**
 * Model a unique job to be managed
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includePackage = false, includeNames = true)
@Canonical
@CompileStatic
class JobSpec {
    enum Type { Transfer, Build, Scan }

    /**
     * The type of the job. See {@link Type}
     */
    final Type type

    /**
     * The unique id of the state record associated with this job
     */
    final String stateId

    /**
     * The instant when this job was created
     */
    final Instant creationTime

    /**
     * The max time to live of the job
     */
    final Duration maxDuration

    /**
     * The unique name of the underlying infra operation associated with this job
     * e.g. the K8s job name or Docker container name
     */
    final String operationName

    /**
     * The temporary path associated with this job (optional)
     */
    final Path cleanableDir

}
