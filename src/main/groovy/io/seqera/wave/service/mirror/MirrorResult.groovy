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

import java.time.Duration
import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.seqera.wave.core.ContainerPlatform
import jakarta.inject.Singleton
/**
 * Model a container mirror entry object
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includeNames = true, includePackage = false)
@Singleton
@CompileStatic
@Canonical
class MirrorResult {

    enum Status { PENDING, COMPLETED }

    final String mirrorId
    final String digest
    final String sourceImage
    final String targetImage
    final ContainerPlatform platform
    final Instant creationTime
    final String offsetId
    final String userName
    final String userEmail
    final Long userId
    final String scanId
    final Status status
    final Duration duration
    final Integer exitCode
    final String logs

    boolean succeeded() {
        status==Status.COMPLETED && exitCode==0
    }

    MirrorResult complete(Integer exitCode, String logs) {
        new MirrorResult(
                this.mirrorId,
                this.digest,
                this.sourceImage,
                this.targetImage,
                this.platform,
                this.creationTime,
                this.offsetId,
                this.userName,
                this.userEmail,
                this.userId,
                this.scanId,
                Status.COMPLETED,
                Duration.between(this.creationTime, Instant.now()),
                exitCode,
                logs
        )
    }

    static MirrorResult of(MirrorRequest request) {
        new MirrorResult(
                request.mirrorId,
                request.digest,
                request.sourceImage,
                request.targetImage,
                request.platform,
                request.creationTime,
                request.offsetId,
                request.identity?.user?.userName,
                request.identity?.user?.email,
                request.identity?.user?.id,
                request.scanId,
                Status.PENDING
        )
    }

    String getMirrorId() {
        return mirrorId
    }

    String getDigest() {
        return digest
    }

    String getSourceImage() {
        return sourceImage
    }

    String getTargetImage() {
        return targetImage
    }

    ContainerPlatform getPlatform() {
        return platform
    }

    Instant getCreationTime() {
        return creationTime
    }

    String getOffsetId() {
        return offsetId
    }

    String getUserName() {
        return userName
    }

    String getUserEmail() {
        return userEmail
    }

    Long getUserId() {
        return userId
    }

    String getScanId() {
        return scanId
    }

    Status getStatus() {
        return status
    }

    Duration getDuration() {
        return duration
    }

    Integer getExitCode() {
        return exitCode
    }

    String getLogs() {
        return logs
    }
}
