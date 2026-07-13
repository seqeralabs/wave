/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.controller.v1

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.service.pairing.PairingRecord
import io.seqera.service.pairing.PairingService
import io.seqera.wave.api.v1.model.ContainerInspectRequest
import io.seqera.wave.api.v1.model.ContainerInspectResponse
import io.seqera.wave.core.spec.ConfigSpec
import io.seqera.wave.core.spec.ContainerSpec
import io.seqera.wave.core.spec.ManifestSpec
import io.seqera.wave.core.spec.ObjectRef
import io.seqera.wave.model.ContainerOrIndexSpec
import io.seqera.wave.service.UserService
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import io.seqera.wave.tower.auth.JwtAuth
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class InspectionsV1ControllerTest extends Specification {

    @Inject @Client('/') HttpClient client
    @Inject ContainerInspectService inspectService
    @Inject PairingService pairingService
    @Inject UserService userService

    @MockBean(ContainerInspectService)
    ContainerInspectService mockInspectService() { Mock(ContainerInspectService) }

    @MockBean(PairingService)
    PairingService mockPairingService() { Mock(PairingService) }

    @MockBean(UserService)
    UserService mockUserService() { Mock(UserService) }

    private ContainerOrIndexSpec buildSpec(String image) {
        def configSpec = new ConfigSpec(['architecture': 'amd64', 'container': 'abc123', 'config': [:], 'rootfs': [type: 'layers', diff_ids: ['sha256:abc']]])
        def manifestSpec = new ManifestSpec(
                2,
                'application/vnd.docker.distribution.manifest.v2+json',
                new ObjectRef('application/vnd.docker.container.image.v1+json', 'sha256:config', 1234L, Map.of()),
                [new ObjectRef('application/vnd.docker.image.rootfs.diff.tar.gzip', 'sha256:layer1', 5678L, Map.of())],
                Map.of(),
                null
        )
        def (registry, repo, tag) = [image.split('/')[0], image.split('/')[1..-2].join('/') + '/' + image.split('/')[-1].split(':')[0], image.split(':')[-1]]
        def containerSpec = new ContainerSpec(registry, "registry-1.${registry}", repo, tag, 'sha256:deadbeef', configSpec, manifestSpec)
        return new ContainerOrIndexSpec(containerSpec)
    }

    def 'POST /w1/inspections returns 200 with mapped ContainerInspectResponse (anonymous)'() {
        given:
        def req = new ContainerInspectRequest()
                .containerImage('docker.io/library/busybox:latest')

        def configSpec = new ConfigSpec(['architecture': 'amd64', 'container': 'abc123', 'config': [:], 'rootfs': [type: 'layers', diff_ids: ['sha256:abc']]])
        def manifestSpec = new ManifestSpec(
                2,
                'application/vnd.docker.distribution.manifest.v2+json',
                new ObjectRef('application/vnd.docker.container.image.v1+json', 'sha256:config', 1234L, Map.of()),
                [new ObjectRef('application/vnd.docker.image.rootfs.diff.tar.gzip', 'sha256:layer1', 5678L, Map.of())],
                Map.of(),
                null
        )
        def containerSpec = new ContainerSpec('docker.io', 'registry-1.docker.io', 'library/busybox', 'latest', 'sha256:deadbeef', configSpec, manifestSpec)
        def spec = new ContainerOrIndexSpec(containerSpec)

        inspectService.containerOrIndexSpec('docker.io/library/busybox:latest', null, PlatformId.NULL) >> spec

        when:
        def resp = client.toBlocking()
                .exchange(HttpRequest.POST('/w1/inspections', req), ContainerInspectResponse)

        then:
        resp.status == HttpStatus.OK
        resp.body().container.registry == 'docker.io'
        resp.body().container.hostName == 'registry-1.docker.io'
        resp.body().container.imageName == 'library/busybox'
        resp.body().container.reference == 'latest'
        resp.body().container.digest == 'sha256:deadbeef'
        resp.body().container.v2 == true
        resp.body().container.v1 == false
        resp.body().container.oci == false
        resp.body().container.config.architecture == 'amd64'
        resp.body().container.manifest.schemaVersion == 2
        resp.body().container.manifest.mediaType == 'application/vnd.docker.distribution.manifest.v2+json'
        resp.body().container.manifest.config.digest == 'sha256:config'
        resp.body().container.manifest.layers.size() == 1
        resp.body().container.manifest.layers[0].digest == 'sha256:layer1'
    }

    def 'POST /w1/inspections returns 404 when service returns null'() {
        given:
        def req = new ContainerInspectRequest()
                .containerImage('docker.io/library/notfound:latest')

        inspectService.containerOrIndexSpec('docker.io/library/notfound:latest', null, PlatformId.NULL) >> null

        when:
        client.toBlocking()
                .exchange(HttpRequest.POST('/w1/inspections', req), ContainerInspectResponse)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }

    def 'POST /w1/inspections resolves PlatformId when Tower credentials are provided'() {
        given:
        def token = 'my-tower-token'
        def endpoint = 'https://api.cloud.seqera.io'
        def workspaceId = 42L
        def req = new ContainerInspectRequest()
                .containerImage('docker.io/library/alpine:3')
                .towerAccessToken(token)
                .towerEndpoint(endpoint)
                .towerWorkspaceId(workspaceId)

        def mockUser = new User(id: 1L, email: 'user@example.com', userName: 'testuser')
        def registration = new PairingRecord(endpoint: endpoint)
        def configSpec = new ConfigSpec(['architecture': 'amd64', 'container': 'abc123', 'config': [:], 'rootfs': [type: 'layers', diff_ids: ['sha256:abc']]])
        def manifestSpec = new ManifestSpec(
                2,
                'application/vnd.docker.distribution.manifest.v2+json',
                new ObjectRef('application/vnd.docker.container.image.v1+json', 'sha256:config', 100L, Map.of()),
                [new ObjectRef('application/vnd.docker.image.rootfs.diff.tar.gzip', 'sha256:layer1', 200L, Map.of())],
                Map.of(),
                null
        )
        def containerSpec = new ContainerSpec('docker.io', 'registry-1.docker.io', 'library/alpine', '3', 'sha256:alpine', configSpec, manifestSpec)
        def spec = new ContainerOrIndexSpec(containerSpec)

        pairingService.getPairingRecord(PairingService.TOWER_SERVICE, endpoint) >> registration
        userService.getUserByAccessToken(endpoint, _ as JwtAuth) >> mockUser
        inspectService.containerOrIndexSpec('docker.io/library/alpine:3', null, _ as PlatformId) >> spec

        when:
        def resp = client.toBlocking()
                .exchange(HttpRequest.POST('/w1/inspections', req), ContainerInspectResponse)

        then:
        resp.status == HttpStatus.OK
        resp.body().container.imageName == 'library/alpine'
        resp.body().container.digest == 'sha256:alpine'
    }
}
