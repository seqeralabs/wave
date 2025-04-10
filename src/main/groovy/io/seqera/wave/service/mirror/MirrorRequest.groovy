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
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.PlatformId
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
     * The timestamp when the request has been submitted
     */
    final Instant creationTime

    /**
     * Request timezone offset
     */
    final String offsetId

    /**
     * Platform identity of the user that created this request
     */
    final PlatformId identity

    static MirrorRequest create(String sourceImage, String targetImage, String digest, ContainerPlatform platform, Path workspace, String authJson, String scanId, Instant ts, String offsetId, PlatformId identity) {
        assert sourceImage, "Argument 'sourceImage' cannot be null"
        assert targetImage, "Argument 'targetImage' cannot be empty"
        assert workspace, "Argument 'workspace' cannot be null"
        assert digest, "Argument 'digest' cannot be empty"

        final mirrorId = ID_PREFIX + LongRndKey.rndHex()
        return new MirrorRequest(
                mirrorId,
                sourceImage,
                targetImage,
                digest,
                platform,
                workspace.resolve(mirrorId),
                authJson,
                scanId,
                ts,
                offsetId,
                identity
        )
    }

    String getMirrorId() {
        return mirrorId
    }

    String getSourceImage() {
        return sourceImage
    }

    String getTargetImage() {
        return targetImage
    }

    String getDigest() {
        return digest
    }

    ContainerPlatform getPlatform() {
        return platform
    }

    Path getWorkDir() {
        return workDir
    }

    String getAuthJson() {
        return authJson
    }

    String getScanId() {
        return scanId
    }

    Instant getCreationTime() {
        return creationTime
    }

    String getOffsetId() {
        return offsetId
    }

    PlatformId getIdentity() {
        return identity
    }

    static MirrorRequest of(Map opts) {
        new MirrorRequest(
                opts.mirrorId as String,
                opts.sourceImage as String,
                opts.targetImage as String,
                opts.digest as String,
                opts.platform as ContainerPlatform,
                opts.workDir as Path,
                opts.authJson as String,
                opts.scanId as String,
                opts.creationTime as Instant,
                opts.offsetId as String,
                opts.identity as PlatformId
        )
    }
}
