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

package io.seqera.wave.service.scan

import java.nio.file.Path
import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.seqera.wave.core.ContainerPlatform
/**
 * Model a container scan request
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class ScanRequest {

    /**
     * The scan unique id
     */
    final String scanId

    /**
     * The container build that generated this scan operation, either a container, build or mirror request
     */
    final String buildId

    /**
     * The container mirror that generated this scan operation, either a container, build or mirror request
     */
    final String mirrorId

    /**
     * The container request that generated this scan operation, either a container, build or mirror request
     */
    final String requestId

    /**
     * The docker config json required to authenticate this request
     */
    final String configJson

    /**
     * The container image that needs to be scanned
     */
    final String targetImage

    /**
     * The container platform to be used
     */
    final ContainerPlatform platform

    /**
     * The scan job work directory
     */
    final Path workDir

    /**
     * Scan request creation time
     */
    final Instant creationTime


    static ScanRequest of(Map opts) {
        new ScanRequest(
                opts.scanId as String,
                opts.buildId as String,
                opts.mirrorId as String,
                opts.requestId as String,
                opts.configJson as String,
                opts.targetImage as String,
                opts.platform as ContainerPlatform,
                opts.workDir as Path,
                opts.creationTime as Instant
        )
    }

}
