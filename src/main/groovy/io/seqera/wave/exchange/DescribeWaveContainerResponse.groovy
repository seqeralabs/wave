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

package io.seqera.wave.exchange

import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.tower.User

/**
 * Model a container request record
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class DescribeWaveContainerResponse {

    @Canonical
    static class RequestInfo {
        final User user
        final Long workspaceId
        final String containerImage
        final ContainerConfig containerConfig
        final String platform
        final String towerEndpoint
        final String fingerprint
        final Instant timestamp
        final String zoneId
        final String ipAddress

        RequestInfo() {}

        RequestInfo(WaveContainerRecord data) {
            this.user = data.user
            this.workspaceId = data.workspaceId
            this.containerImage = data.containerImage
            this.containerConfig = data.containerConfig
            this.platform = data.platform
            this.towerEndpoint = data.towerEndpoint
            this.fingerprint = data.fingerprint
            this.timestamp = data.timestamp
            this.zoneId = data.zoneId
            this.ipAddress = data.ipAddress
        }
    }

    @Canonical
    static class BuildInfo {
        final String containerFile
        final String condaFile
        final String buildRepository
        final String cacheRepository
        final String buildId
        final Boolean buildNew
        final Boolean freeze

        BuildInfo() {}

        BuildInfo(WaveContainerRecord data) {
            this.containerFile = data.containerFile
            this.condaFile = data.condaFile
            this.buildRepository = data.buildRepository
            this.cacheRepository = data.cacheRepository
            this.buildId = data.buildId
            this.buildNew = data.buildNew
            this.freeze = data.freeze
        }
    }

    @Canonical
    static class ContainerInfo {
        String image
        String digest
    }

    final String token

    final Instant expiration

    final RequestInfo request

    final BuildInfo build

    final ContainerInfo source

    final ContainerInfo wave

    static DescribeWaveContainerResponse create(String token, WaveContainerRecord data) {
        final request = new RequestInfo(data)
        final build = new BuildInfo(data)
        final source = new ContainerInfo(data.sourceImage, data.sourceDigest)
        final wave = new ContainerInfo(data.waveImage, data.waveDigest)
        return new DescribeWaveContainerResponse(token, data.expiration, request, build, source, wave)
    }
}
