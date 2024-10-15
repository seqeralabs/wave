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

    enum Type { Container, Build, Mirror }

    Type type
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
    String scanId
    ScanMode scanMode
    List<ScanLevel> scanLevels
    boolean dryRun
    Boolean succeeded
    Instant creationTime

    boolean durable() {
        return freeze || getMirror()
    }

    PlatformId getIdentity() {
        return identity
    }

    boolean isContainer() {
        type==Type.Container
    }

    ContainerCoordinates coordinates() { ContainerCoordinates.parse(containerImage) }

    @Override
    String toString() {
        return "ContainerRequest[" +
                "type=${type}; " +
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
                "scanId=${scanId}; " +
                "scanMode=${scanMode}; " +
                "scanLevels=${scanLevels}; " +
                "dryRun=${dryRun}; " +
                "succeeded=${succeeded}; " +
                "creationTime=${creationTime}; " +
                "]"
    }

    Type getType() {
        return type
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
        return type==Type.Mirror
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

    boolean getDryRun() {
        return dryRun
    }

    Boolean getSucceeded() {
        return succeeded
    }

    Instant getCreationTime() {
        return creationTime
    }

    static ContainerRequest create(
            Type type,
            PlatformId identity,
            String containerImage,
            String containerFile,
            ContainerConfig containerConfig,
            String condaFile,
            ContainerPlatform platform,
            String buildId,
            Boolean buildNew,
            Boolean freeze,
            String scanId,
            ScanMode scanMode,
            List<ScanLevel> scanLevels,
            boolean dryRun,
            Boolean succeeded,
            Instant creationTime
    )
    {
        return new ContainerRequest(
                type,
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
                scanId,
                scanMode,
                scanLevels,
                dryRun,
                succeeded,
                creationTime
        )
    }

    static ContainerRequest of(PlatformId identity) {
        new ContainerRequest(Type.Container, (String)null, identity)
    }
    
    static ContainerRequest of(Map data) {
        new ContainerRequest(
                data.type as Type,
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
                data.scanId as String,
                data.scanMode as ScanMode,
                data.scanLevels as List<ScanLevel>,
                data.dryRun as boolean,
                data.succeeded as Boolean,
                data.creationTime as Instant
        )
    }
}
