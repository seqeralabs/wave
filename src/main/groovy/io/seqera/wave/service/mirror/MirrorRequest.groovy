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

import java.nio.file.Path
import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.seqera.wave.api.ScanMode
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.util.LongRndKey

/**
 * Model a container mirror request
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includeNames = true, includePackage = false)
@Canonical
@CompileStatic
class MirrorRequest {

    static final String ID_PREFIX = 'mr-'

    /**
     * Unique id of the request
     */
    final String mirrorId

    /**
     * The container image to be mirrored
     */
    final String sourceImage

    /**
     * The target image name where the container should be mirrored
     */
    final String targetImage

    /**
     * The (SHA256) digest of the container to be mirrored
     */
    final String digest

    /**
     * The container platform to be copied
     */
    final ContainerPlatform platform

    /**
     * The work directory used by the mirror operation
     */
    final Path workDir

    /**
     * Docker config json to authorise the mirror (pull & push) operation
     */
    final String authJson

    /**
     * The scanId associated this mirror request
     */
    final String scanId

    /**
     * The scanMode associated with this request
     */
    final ScanMode scanMode

    /**
     * The timestamp when the request has been submitted
     */
    final Instant creationTime

    static MirrorRequest create(String sourceImage, String targetImage, String digest, ContainerPlatform platform, Path workspace, String authJson, String scanId, ScanMode scanMode, Instant ts = Instant.now()) {
        assert sourceImage, "Argument 'sourceImage' cannot be null"
        assert targetImage, "Argument 'targetImage' cannot be empty"
        assert workspace, "Argument 'workspace' cannot be null"
        assert digest, "Argument 'digest' cannot be empty"

        final id = LongRndKey.rndHex()
        return new MirrorRequest(
                ID_PREFIX + id,
                sourceImage,
                targetImage,
                digest,
                platform,
                workspace.resolve("mirror-${id}"),
                authJson,
                scanId,
                scanMode,
                ts )
    }
}
