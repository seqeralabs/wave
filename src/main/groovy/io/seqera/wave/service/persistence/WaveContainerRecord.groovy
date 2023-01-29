package io.seqera.wave.service.persistence

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.tower.User
/**
 * Model a Wave container request record 
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includeNames = true, includePackage = false)
@Canonical
@CompileStatic
class WaveContainerRecord {

    @Canonical
    static class ContainerInfo {
        String image
        String digest

        ContainerInfo withImage(String image) {
            return new ContainerInfo(image, this.digest)
        }

        ContainerInfo withDigest(String digest) {
            return new ContainerInfo(this.image, digest)
        }
    }

    /**
     * The Tower user associated with the request
     */
    final User user

    /**
     * The Tower workspace associated with the request
     */
    final Long workspaceId

    /**
     * The container image requested. this can be null null when a build request was submitted
     */
    final String containerImage

    /**
     * The container file (aka Dockefile) content associated with the request
     */
    final String containerFile

    /**
     * The container config associated with the request
     */
    final ContainerConfig containerConfig

    /**
     * The conda file associated with the request
     */
    final String condaFile

    /**
     * The container arch platform
     */
    final String platform

    /**
     * The Tower endpoint associated with the request
     */
    final String towerEndpoint

    /**
     * The repository where the build image is uploaded
     */
    final String buildRepository

    /**
     * The repository where container layers are cached
     */
    final String cacheRepository

    /**
     * The request fingerprint
     */
    final String fingerprint

    /**
     * The request timestamp
     */
    final String timestamp

    /**
     * The IP address originating the request
     */
    final String ipAddress

    /**
     * The resolved target container image
     */
    final String sourceImage

    final String sourceDigest

    final String waveImage

    final String waveDigest


    WaveContainerRecord(SubmitContainerTokenRequest request, ContainerRequestData data, String waveContainerImage, User user, String addr) {
        this.user = user
        this.workspaceId = request.towerWorkspaceId
        this.containerImage = request.containerImage
        this.containerConfig = request.containerConfig
        this.platform = request.containerPlatform
        this.towerEndpoint = request.towerEndpoint
        this.buildRepository = request.buildRepository
        this.cacheRepository = request.cacheRepository
        this.fingerprint = request.fingerprint
        this.timestamp = request.timestamp
        this.ipAddress = addr
        this.condaFile = data.condaFile
        this.containerFile = data.containerFile
        this.sourceImage = data.containerImage
        this.waveImage = waveContainerImage
    }

    WaveContainerRecord(WaveContainerRecord that, String sourceDigest, String waveDigest) {
        this.user = that.user
        this.workspaceId = that.workspaceId
        this.containerImage = that.containerImage
        this.containerConfig = that.containerConfig
        this.platform = that.platform
        this.towerEndpoint = that.towerEndpoint
        this.buildRepository = that.buildRepository
        this.cacheRepository = that.cacheRepository
        this.fingerprint = that.fingerprint
        this.timestamp = that.timestamp
        this.ipAddress = that.ipAddress
        this.condaFile = that.condaFile
        this.containerFile = that.containerFile
        this.sourceDigest = sourceDigest
        this.waveDigest = waveDigest
    }

    /**
     * Required by jackson ser/de-ser
     */
    protected WaveContainerRecord() { }
}
