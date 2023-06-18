package io.seqera.wave.api

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SubmitContainerTokenRequestTest extends Specification {

    def 'should copy a request' () {
        given:
        def req = new SubmitContainerTokenRequest(
                towerAccessToken: 'a1',
                towerRefreshToken: 'a2',
                towerEndpoint: 'a3',
                towerWorkspaceId: 4,
                containerImage: 'a5',
                containerFile:  'a6',
                containerConfig: new ContainerConfig(entrypoint: ['this','that']),
                condaFile: 'a8',
                spackFile: 'a9',
                containerPlatform: 'a10',
                buildRepository: 'a11',
                cacheRepository: 'a12',
                timestamp: 'a13',
                fingerprint: 'a14',
                forceBuild: true,
                sealedMode: true
        )

        when:
        def copy = req.copyWith(Map.of())
        then:
        copy.towerAccessToken == req.towerAccessToken
        copy.towerRefreshToken == req.towerRefreshToken
        copy.towerEndpoint == req.towerEndpoint
        copy.towerWorkspaceId == req.towerWorkspaceId
        copy.containerImage == req.containerImage
        copy.containerFile == req.containerFile
        copy.containerConfig == req.containerConfig
        copy.condaFile == req.condaFile
        copy.spackFile == req.spackFile
        copy.containerPlatform == req.containerPlatform
        copy.buildRepository == req.buildRepository
        copy.cacheRepository == req.cacheRepository
        copy.timestamp == req.timestamp
        copy.fingerprint == req.fingerprint
        copy.forceBuild == req.forceBuild
        copy.sealedMode == req.sealedMode


        when:
        def other = req.copyWith(
                towerAccessToken: 'b1',
                towerRefreshToken: 'b2',
                towerEndpoint: 'b3',
                towerWorkspaceId: 44,
                containerImage: 'b5',
                containerFile:  'b6',
                containerConfig: new ContainerConfig(entrypoint: ['foo','bar']),
                condaFile: 'b8',
                spackFile: 'b9',
                containerPlatform: 'b10',
                buildRepository: 'b11',
                cacheRepository: 'b12',
                timestamp: 'b13',
                fingerprint: 'b14',
                forceBuild: false,
                sealedMode: false
        )
        then:
        other.towerAccessToken == 'b1'
        other.towerRefreshToken == 'b2'
        other.towerEndpoint == 'b3'
        other.towerWorkspaceId == 44
        other.containerImage == 'b5'
        other.containerFile == 'b6'
        other.containerConfig == new ContainerConfig(entrypoint: ['foo','bar'])
        other.condaFile == 'b8'
        other.spackFile == 'b9'
        other.containerPlatform == 'b10'
        other.buildRepository == 'b11'
        other.cacheRepository == 'b12'
        other.timestamp == 'b13'
        other.fingerprint == 'b14'
        other.forceBuild == false
        other.sealedMode == false
    }

}
