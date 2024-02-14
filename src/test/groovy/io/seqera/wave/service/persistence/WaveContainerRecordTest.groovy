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

package io.seqera.wave.service.persistence

import spock.lang.Specification

import java.time.Instant
import java.time.OffsetDateTime

import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class WaveContainerRecordTest extends Specification {
    
    def 'should create wave record' () {
        given:
        def lyr = new ContainerLayer(location: 'data:12346')
        def cfg = new ContainerConfig(entrypoint: ['/opt/fusion'], layers: [lyr])
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
        def user = new User(id:1, organization: 'myorg')
        def data = new ContainerRequestData(new PlatformId(user,100), 'hello-world', 'some docker', cfg, 'some conda')
        def wave = 'https://wave.io/some/container:latest'
        def addr = '100.200.300.400'
        
        when:
        def exp = Instant.now().plusSeconds(3600)
        def container = new WaveContainerRecord(req, data, wave, addr, exp)
        then:
        container.user == user
        container.workspaceId == req.towerWorkspaceId
        container.containerImage == req.containerImage
        container.containerConfig == ContainerConfig.copy(req.containerConfig, true)
        container.platform == req.containerPlatform
        container.towerEndpoint == req.towerEndpoint
        container.buildRepository == req.buildRepository
        container.cacheRepository == req.cacheRepository
        container.fingerprint == req.fingerprint
        container.ipAddress == addr
        container.condaFile == data.condaFile
        container.containerFile == data.containerFile
        container.sourceImage == data.containerImage
        container.waveImage == wave
        container.timestamp == OffsetDateTime.parse(req.timestamp).toInstant()
        container.zoneId == OffsetDateTime.parse(req.timestamp).offset.id
        container.expiration == exp
    }
}
