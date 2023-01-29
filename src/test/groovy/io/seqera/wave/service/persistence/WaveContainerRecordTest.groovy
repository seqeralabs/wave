package io.seqera.wave.service.persistence

import spock.lang.Specification

import java.time.Instant

import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.tower.User

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class WaveContainerRecordTest extends Specification {
    
    def 'should create wave record' () {
        given:
        def cfg = new ContainerConfig(entrypoint: ['/opt/fusion'])
        def req = new SubmitContainerTokenRequest(
                towerEndpoint: 'https://tower.nf',
                towerWorkspaceId: 100,
                containerConfig: cfg,
                containerPlatform: ContainerPlatform.of('amd64'),
                buildRepository: 'build.docker.io',
                cacheRepository: 'cache.docker.io',
                fingerprint: 'xyz',
                timestamp: Instant.now().toString() )
        and:
        def data = new ContainerRequestData(1, 100, 'hello-world', 'some docker', cfg, 'some conda')
        def user = new User()
        def wave = 'https://wave.io/some/container:latest'
        def addr = '100.200.300.400'
        
        when:
        def container = new WaveContainerRecord(req, data, wave, user, addr)
        then:
        container.user == user
        container.workspaceId == req.towerWorkspaceId
        container.containerImage == req.containerImage
        container.containerConfig == req.containerConfig
        container.platform == req.containerPlatform
        container.towerEndpoint == req.towerEndpoint
        container.buildRepository == req.buildRepository
        container.cacheRepository == req.cacheRepository
        container.fingerprint == req.fingerprint
        container.timestamp == req.timestamp
        container.ipAddress == addr
        container.condaFile == data.condaFile
        container.containerFile == data.containerFile
        container.sourceImage == data.containerImage
        container.waveImage == wave
    }
}
