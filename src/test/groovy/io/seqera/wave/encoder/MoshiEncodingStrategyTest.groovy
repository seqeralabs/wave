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

package io.seqera.wave.encoder

import spock.lang.Specification

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import io.seqera.wave.api.BuildContext
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerStatus
import io.seqera.wave.api.ContainerStatusResponse
import io.seqera.wave.auth.RegistryAuth
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildEntry
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.pairing.socket.msg.PairingHeartbeat
import io.seqera.wave.service.pairing.socket.msg.PairingResponse
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpRequest
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpResponse
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.request.ContainerRequest
import io.seqera.wave.storage.DigestStore
import io.seqera.wave.storage.DockerDigestStore
import io.seqera.wave.storage.HttpDigestStore
import io.seqera.wave.storage.ZippedDigestStore
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
class MoshiEncodingStrategyTest extends Specification {

    def 'should encode and decode build result' () {
        given:
        def encoder = new MoshiEncodeStrategy<BuildResult>() { }
        and:
        def build = BuildResult.completed('1', 2, 'Oops', Instant.now(), null)

        when:
        def json = encoder.encode(build)
        and:
        def copy = encoder.decode(json)
        then:
        copy.getClass() == build.getClass()
        and:
        copy == build
    }

    def 'should decode old format build result' () {
        given:
        def encoder = new MoshiEncodeStrategy<BuildResult>() { }
        and:
        def json = '{"id":"100","exitStatus":1,"logs":"logs","startTime":"2022-12-03T22:27:04.079724Z","duration":60.000000000}'

        when:
        def build = encoder.decode(json)
        then:
        build.buildId == '100'
        build.exitStatus == 1
        build.logs == 'logs'
        build.startTime == Instant.parse('2022-12-03T22:27:04.079724Z')
        build.duration == Duration.ofSeconds(60)
    }

    def 'should encode and decode ContainerRequestData' () {
        given:
        def encoder = new MoshiEncodeStrategy<ContainerRequest>() { }
        and:
        def data = ContainerRequest.of(
                identity: new PlatformId(new User(id:1),2),
                containerImage:  'ubuntu',
                containerFile:  'from foo',
                containerConfig:  new ContainerConfig(entrypoint: ['some', 'entry'], cmd:['the', 'cmd']),
                condaFile: 'some/conda/file',
                platform: ContainerPlatform.of('amd64') )

        when:
        def json = encoder.encode(data)
        and:
        def copy = encoder.decode(json)
        then:
        copy.getClass() == data.getClass()
        and:
        copy == data
    }

    def 'should deserialize container request data' () {
        given:
        def REQUEST = '''
            {
               "buildId":"build-123",
               "buildNew":true,
               "condaFile":"some/conda/file",
               "containerConfig":{
                  "cmd":[ "the","cmd" ],
                  "entrypoint":["some","entry" ]
               },
               "containerFile":"from foo",
               "containerImage":"ubuntu",
               "freeze":true,
               "identity":{
                  "user":{
                     "id":1,
                     "email": "foo@gmail.com",
                     "userName": "foo"
                  },
                  "workspaceId":2,
                  "accessToken": "12345",
                  "towerEndpoint": "https://foo.com"
               },
               "platform":{
                  "arch":"amd64",
                  "os":"linux"
               }
            }
            '''
        and:
        def encoder = new MoshiEncodeStrategy<ContainerRequest>() { }

        when:
        def result = encoder.decode(REQUEST)
        then:
        result.identity == new PlatformId(new User(id:1, email: "foo@gmail.com", userName: 'foo'), 2, '12345', 'https://foo.com')
        result.containerImage == 'ubuntu'
        result.containerFile == 'from foo'
        result.containerConfig == new ContainerConfig(entrypoint: ['some', 'entry'], cmd:['the', 'cmd'])
        result.condaFile == 'some/conda/file'
        result.platform == ContainerPlatform.of('amd64')
        result.buildId == 'build-123'
        result.buildNew
        result.freeze
    }

    def 'should encode and decode zipped digest store' () {
        given:
        def DATA = 'Hello wold!'
        def encoder = new MoshiEncodeStrategy<DigestStore>() { }
        and:
        def data = ZippedDigestStore.fromUncompressed(DATA.bytes, 'my/media', '12345', 2000)

        when:
        def json = encoder.encode(data)
        println json

        and:
        def copy = encoder.decode(json)
        then:
        copy.getClass() == data.getClass()
        and:
        copy.bytes == data.bytes
        copy.digest == data.digest
        copy.mediaType == data.mediaType
        copy.size == data.size
        and:
        new String(copy.bytes) == DATA
    }

    def 'should encode and decode proxy http request message' () {
        given:
        def encoder = new MoshiEncodeStrategy<ProxyHttpRequest>() { }
        and:
        def data = new ProxyHttpRequest(
                msgId: 'foo',
                method: 'GET',
                uri: 'http://localhost',
                body: 'body',
                auth: 'secret',
                headers: ['name': ['val1', 'val2']]
        )

        when:
        def json = encoder.encode(data)
        println json

        and:
        def copy = encoder.decode(json)
        then:
        copy.getClass() == data.getClass()
        and:
        copy.msgId == data.msgId
        copy.method == data.method
        copy.uri == data.uri
        copy.body == data.body
        copy.auth == data.auth
        copy.headers == data.headers
    }

    def 'should encode and decode proxy http response message' () {
        given:
        def encoder = new MoshiEncodeStrategy<ProxyHttpResponse>() { }
        and:
        def data = new ProxyHttpResponse(
                msgId: 'foo',
                status: 200,
                body: 'body',
                headers: ['name': ['val1', 'val2']]
        )

        when:
        def json = encoder.encode(data)
        println json

        and:
        def copy = encoder.decode(json)
        then:
        copy.getClass() == data.getClass()
        and:
        copy.msgId == data.msgId
        copy.status == data.status
        copy.body == data.body
        copy.headers == data.headers
    }

    def 'should encode and decode pairing heartbeat message' () {
        given:
        def encoder = new MoshiEncodeStrategy<PairingHeartbeat>() { }
        and:
        def data = new PairingHeartbeat(msgId: 'foo')

        when:
        def json = encoder.encode(data)
        println json

        and:
        def copy = encoder.decode(json)
        then:
        copy.getClass() == data.getClass()
        and:
        copy.msgId == data.msgId
    }

    def 'should encode and decode pairing response message' () {
        given:
        def encoder = new MoshiEncodeStrategy<PairingResponse>() { }
        and:
        def data = new PairingResponse(msgId: 'foo', publicKey: 'key', pairingId: 'id')

        when:
        def json = encoder.encode(data)
        println json

        and:
        def copy = encoder.decode(json)
        then:
        copy.getClass() == data.getClass()
        and:
        copy.msgId == data.msgId
        copy.publicKey == data.publicKey
        copy.pairingId == data.pairingId
    }

    def 'should encode and decode http digest store' () {
        given:
        def encoder = new MoshiEncodeStrategy<DigestStore>() { }
        and:
        def data = new HttpDigestStore(
                'http://foo.com/this/that',
                'text/json',
                '12345',
                2000 )

        when:
        def json = encoder.encode(data)

        and:
        def copy = (HttpDigestStore) encoder.decode(json)
        then:
        copy.getClass() == data.getClass()
        and:
        copy.location == data.location
        copy.digest == data.digest
        copy.mediaType == data.mediaType
        copy.size == data.size
    }

    def 'should encode and decode http digest store' () {
        given:
        def encoder = new MoshiEncodeStrategy<DigestStore>() { }
        and:
        def data = new DockerDigestStore(
                'docker://foo.com/this/that',
                'text/json',
                '12345',
                2000 )

        when:
        def json = encoder.encode(data)

        and:
        def copy = (DockerDigestStore) encoder.decode(json)
        then:
        copy.getClass() == data.getClass()
        and:
        copy.location == data.location
        copy.digest == data.digest
        copy.mediaType == data.mediaType
        copy.size == data.size
    }

    def 'should encode and decode wave build record' () {
        given:
        def encoder = new MoshiEncodeStrategy<WaveBuildRecord>() { }
        and:
        def context = new BuildContext('http://foo.com', '12345', 100, '67890')
        and:
        def build = new BuildRequest(
                containerId: '12345',
                containerFile: 'from foo',
                condaFile: 'conda spec',
                workspace:  Path.of("/some/path"),
                targetImage:  'docker.io/some:image:12345',
                identity: PlatformId.NULL,
                platform:  ContainerPlatform.of('linux/amd64'),
                cacheRepository:  'cacherepo',
                ip: "1.2.3.4",
                configJson:  '{"config":"json"}',
                scanId: 'scan12345',
                buildContext: context,
                buildId: '12345_1',
        )
        def result = new BuildResult(build.buildId, -1, "ok", Instant.now(), Duration.ofSeconds(3), null)
        def event = new BuildEvent(build, result)

        and:
        def record1 = WaveBuildRecord.fromEvent(event)

        when:
        def json = encoder.encode(record1)
        and:
        def copy = encoder.decode(json)
        then:
        copy.getClass() == record1.getClass()
        and:
        copy == record1
    }

    def 'should decode legacy record with spack config' () {
        given:
        def legacy = '{"buildId":"12345_1","condaFile":"conda spec","dockerFile":"from foo","duration":"3000000000","exitStatus":-1,"platform":"linux/amd64","requestIp":"1.2.3.4","scanId":"scan12345","spackFile":"spack spec","targetImage":"docker.io/some:image:12345"}\n'
        and:
        def encoder = new MoshiEncodeStrategy<WaveBuildRecord>() { }

        // verify decoding is OK when the payload contains `spackFile` not existing anymore in the WaveBuildRecord
        when:
        def rec = encoder.decode(legacy)
        then:
        rec.buildId == '12345_1'
        rec.condaFile == 'conda spec'
        rec.dockerFile == 'from foo'
    }

    def 'should encode and decode registry info' () {
        given:
        def encoder = new MoshiEncodeStrategy<RegistryAuth>() { }
        and:
        def auth = RegistryAuth.parse('Bearer realm="https://auth.docker.io/token",service="registry.docker.io"')

        when:
        def json = encoder.encode(auth)
        and:
        def copy = encoder.decode(json)
        then:
        copy.getClass() == auth.getClass()
        and:
        copy == auth

    }

    def 'should encode and decode build job spec' () {
        given:
        def encoder = new MoshiEncodeStrategy<JobSpec>() { }
        and:
        def ts = Instant.parse('2024-08-18T19:23:33.650722Z')
        and:
        def build = JobSpec.build(
                'docker.io/foo:bar',
                '12345',
                ts,
                Duration.ofMinutes(1),
                Path.of('/some/path') )

        when:
        def json = encoder.encode(build)
        println json
        and:
        def copy = encoder.decode(json)
        then:
        copy == build
    }

    def 'should encode and decode build store entry' () {
        given:
        def encoder = new MoshiEncodeStrategy<BuildEntry>() { }
        def context = new BuildContext('http://foo.com', '12345', 100, '67890')
        and:
        def res = BuildResult.completed('1', 2, 'Oops', Instant.now(), null)
        def req = new BuildRequest(
                containerId: '12345',
                containerFile: 'from foo',
                condaFile: 'conda spec',
                workspace:  Path.of("/some/path"),
                targetImage:  'docker.io/some:image:12345',
                identity: PlatformId.NULL,
                platform:  ContainerPlatform.of('linux/amd64'),
                cacheRepository:  'cacherepo',
                ip: "1.2.3.4",
                configJson:  '{"config":"json"}',
                scanId: 'scan12345',
                buildContext: context,
                buildId: '12345_1',
        )
        and:
        def entry = new BuildEntry(req, res)
        when:
        def json = encoder.encode(entry)
        and:
        def copy = encoder.decode(json)
        then:
        copy.getClass() == entry.getClass()
        and:
        copy == entry
    }

    def 'should encode container status response' () {
        given:
        def encoder =  new MoshiEncodeStrategy<ContainerStatusResponse>() {}
        def timestamp = Instant.parse('2024-10-07T20:41:00.804699Z')
        def resp =     new ContainerStatusResponse(
                '1234',
                ContainerStatus.DONE,
                'bd-12345',
                'mr-12345',
                'sc-12345',
                [LOW: 1, MEDIUM: 2],
                true,
                'Some err message',
                'http://foo.com/view/123',
                timestamp,
                Duration.ofMinutes(1),
        )

        when:
        def result = encoder.encode(resp)
        then:
        result == '{"buildId":"bd-12345","creationTime":"2024-10-07T20:41:00.804699Z","detailsUri":"http://foo.com/view/123","duration":"60000000000","id":"1234","mirrorId":"mr-12345","reason":"Some err message","scanId":"sc-12345","status":"DONE","succeeded":true,"vulnerabilities":{"LOW":1,"MEDIUM":2}}'
    }
}
