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

package io.seqera.wave.tower.client

import java.time.OffsetDateTime

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.seqera.wave.encoder.MoshiSerializable

/**
 * Model a Platform workflow run
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@EqualsAndHashCode
@CompileStatic
@ToString(includePackage = false, includeNames = true)
class Workflow implements MoshiSerializable {

    enum WorkflowStatus {
        SUBMITTED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELLED,
        UNKNOWN
    }

    String id
    OffsetDateTime submit
    OffsetDateTime start
    OffsetDateTime complete
    OffsetDateTime dateCreated
    OffsetDateTime lastUpdated
    String runName
    String sessionId
    String workDir
    String launchId
    WorkflowStatus status
}
