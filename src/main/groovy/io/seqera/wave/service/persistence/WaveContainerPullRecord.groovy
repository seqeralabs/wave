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

package io.seqera.wave.service.persistence

import java.time.Instant

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import io.seqera.wave.core.RoutePath
/**
 * Model a Wave pull request
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@ToString(includeNames = true, includePackage = false)
@EqualsAndHashCode
@CompileStatic
class WaveContainerPullRecord {

    /**
     * The type of artifact is requested in the pull request
     */
    String type

    /**
     * The requested container
     */
    String container

    /**
     * The registry of the container
     */
    String registry

    /**
     * The repository of the container
     */
    String repository

    /**
     * Email id of the user initiated the pull request
     */
    String email

    /**
     * ip address of the user's machine
     */
    String ipAddress

    /**
     * The platform endpoint associated with the request
     */
    String platformEndpoint

    /**
     * The platform workspace associated with the request
     */
    Long workspaceId

    /**
     * Whenever the request is for container with fusion
     */
    String fusionVersion

    /**
     * Whenever the request is for container with fusion
     */
    Instant timestamp

    WaveContainerPullRecord() {}

    WaveContainerPullRecord(RoutePath routePath, String ipAddress){
        this.type = routePath.type
        this.registry = routePath.registry
        this.repository = routePath.repository
        this.container = routePath.targetContainer
        this.platformEndpoint = routePath.identity?.towerEndpoint
        this.email = routePath.identity?.user?.email
        this.ipAddress = ipAddress
        this.workspaceId = routePath.identity?.workspaceId
        this.fusionVersion = routePath.request?.containerConfig?.fusionVersion()?.number
        this.timestamp = Instant.now()
    }
}
