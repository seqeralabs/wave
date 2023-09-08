/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
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

    static ScanRequest fromBuild(BuildRequest request) {
        final id = request.scanId
        final workDir = request.workDir.resolveSibling("scan-${id}")
        return new ScanRequest(id, request.id, request.configJson, request.targetImage, request.platform, workDir)
    }
}
