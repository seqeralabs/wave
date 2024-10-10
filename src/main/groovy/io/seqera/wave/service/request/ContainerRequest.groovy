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

package io.seqera.wave.service.request

import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ScanLevel
import io.seqera.wave.api.ScanMode
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.util.LongRndKey
import static io.seqera.wave.util.StringUtils.trunc
/**
 * Model a container request
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class ContainerRequest {

    String requestId
    PlatformId identity
    String containerImage
    String containerFile
    ContainerConfig containerConfig
    String condaFile
    ContainerPlatform platform
    String buildId
    Boolean buildNew
    Boolean freeze
    Boolean mirror
    String scanId
    ScanMode scanMode
    List<ScanLevel> scanLevels
    boolean scanOnRequest
    boolean dryRun
    Instant creationTime

    boolean durable() {
        return freeze || mirror
    }

    PlatformId getIdentity() {
        return identity
    }

    ContainerCoordinates coordinates() { ContainerCoordinates.parse(containerImage) }

    @Override
    String toString() {
        return "ContainerRequest[" +
                "requestId=${requestId}; " +
                "identity=${getIdentity()}; " +
                "containerImage=$containerImage; " +
                "containerFile=${trunc(containerFile)}; " +
                "condaFile=${trunc(condaFile)}; " +
                "containerConfig=${containerConfig}; " +
                "platform=${platform}; " +
                "buildId=${buildId}; " +
                "buildNew=${buildNew}; " +
                "freeze=${freeze}; " +
                "mirror=${mirror}; " +
                "scanId=${scanId}; " +
                "scanMode=${scanMode}" +
                "scanLevels=${scanLevels}" +
                "scanOnRequest=${scanOnRequest}" +
                "dryRun=${dryRun}" +
                "creationTime=${creationTime}" +
                "]"
    }

    String getRequestId() {
        return requestId
    }

    String getContainerImage() {
        return containerImage
    }

    String getContainerFile() {
        return containerFile
    }

    ContainerConfig getContainerConfig() {
        return containerConfig
    }

    String getCondaFile() {
        return condaFile
    }

    ContainerPlatform getPlatform() {
        return platform
    }

    String getBuildId() {
        return buildId
    }

    Boolean getBuildNew() {
        return buildNew
    }

    Boolean getFreeze() {
        return freeze
    }

    Boolean getMirror() {
        return mirror
    }

    String getScanId() {
        return scanId
    }

    ScanMode getScanMode() {
        return scanMode
    }

    List<ScanLevel> getScanLevels() {
        return scanLevels
    }

    boolean getScanOnRequest() {
        return scanOnRequest
    }

    boolean getDryRun() {
        return dryRun
    }

    Instant getCreationTime() {
        return creationTime
    }


    static ContainerRequest create(
            PlatformId identity,
            String containerImage,
            String containerFile,
            ContainerConfig containerConfig,
            String condaFile,
            ContainerPlatform platform,
            String buildId,
            Boolean buildNew,
            Boolean freeze,
            Boolean mirror,
            String scanId,
            ScanMode scanMode,
            List<ScanLevel> scanLevels,
            boolean scanOnRequest,
            boolean dryRun,
            Instant creationTime
    )
    {
        return new ContainerRequest(
                LongRndKey.rndHex(),
                identity,
                containerImage,
                containerFile,
                containerConfig,
                condaFile,
                platform,
                buildId,
                buildNew,
                freeze,
                mirror,
                scanId,
                scanMode,
                scanLevels,
                scanOnRequest,
                dryRun,
                creationTime
        )
    }

    static ContainerRequest of(PlatformId identity) {
        new ContainerRequest((String)null, identity)
    }
    
    static ContainerRequest of(Map data) {
        new ContainerRequest(
                data.requestId as String,
                data.identity as PlatformId,
                data.containerImage as String,
                data.containerFile as String,
                data.containerConfig as ContainerConfig,
                data.condaFile as String,
                data.platform as ContainerPlatform,
                data.buildId as String,
                (Boolean) data.buildNew,
                (Boolean) data.freeze,
                (Boolean) data.mirror,
                data.scanId as String,
                data.scanMode as ScanMode,
                data.scanLevels as List<ScanLevel>,
                data.scanOnRequest as boolean,
                data.dryRun as boolean,
                data.creationTime as Instant
        )
    }
}
