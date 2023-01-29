package io.seqera.wave.exchange

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
        final String timestamp
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
            this.ipAddress = data.ipAddress
        }
    }

    @Canonical
    static class BuildInfo {
        final String containerFile
        final String condaFile
        final String buildRepository
        final String cacheRepository

        BuildInfo() {}

        BuildInfo(WaveContainerRecord data) {
            this.containerFile = data.containerFile
            this.condaFile = data.condaFile
            this.buildRepository = data.buildRepository
            this.cacheRepository = data.cacheRepository
        }
    }

    @Canonical
    static class ContainerInfo {
        String image
        String digest
    }

    final String token

    final RequestInfo request

    final BuildInfo build

    final ContainerInfo source

    final ContainerInfo wave

    static DescribeWaveContainerResponse create(String token, WaveContainerRecord data) {
        final request = new RequestInfo(data)
        final build = new BuildInfo(data)
        final source = new ContainerInfo(data.sourceImage, data.sourceDigest)
        final wave = new ContainerInfo(data.waveImage, data.waveDigest)
        return new DescribeWaveContainerResponse(token, request, build, source, wave)
    }
}
