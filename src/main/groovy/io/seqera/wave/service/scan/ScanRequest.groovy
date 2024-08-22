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

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildRequest
/**
 * Model a container scan request
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class ScanRequest {
    final String id
    final String buildId
    final String configJson
    final String targetImage
    final ContainerPlatform platform
    final Path workDir
    String s3Key

    static ScanRequest fromBuild(BuildRequest request) {
        final id = request.scanId
        final workDir = request.workDir.resolveSibling("scan-${id}")
        final s3Key = "workspace/scan-${id}"
        return new ScanRequest(id, request.buildId, request.configJson, request.targetImage, request.platform, workDir, s3Key)
    }
}
