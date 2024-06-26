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
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.pairing.socket.msg.PairingHeartbeat
import io.seqera.wave.service.pairing.socket.msg.PairingResponse
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpRequest
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpResponse
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.storage.DigestStore
import io.seqera.wave.storage.DockerDigestStore
import io.seqera.wave.storage.LazyDigestStore
import io.seqera.wave.storage.HttpDigestStore
import io.seqera.wave.storage.ZippedDigestStore
import io.seqera.wave.storage.reader.DataContentReader
import io.seqera.wave.storage.reader.GzipContentReader
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
        build.id == '100'
        build.exitStatus == 1
        build.logs == 'logs'
        build.startTime == Instant.parse('2022-12-03T22:27:04.079724Z')
        build.duration == Duration.ofSeconds(60)
    }

    def 'should encode and decode ContainerRequestData' () {
        given:
        def encoder = new MoshiEncodeStrategy<ContainerRequestData>() { }
        and:
        def data = new ContainerRequestData(
                new PlatformId(new User(id:1),2),
                'ubuntu',
                'from foo',
                new ContainerConfig(entrypoint: ['some', 'entry'], cmd:['the', 'cmd']),
                'some/conda/file',
                ContainerPlatform.of('amd64') )

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
        def encoder = new MoshiEncodeStrategy<ContainerRequestData>() { }

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

    def 'should encode and decode lazy digest store' () {
        given:
        def encoder = new MoshiEncodeStrategy<DigestStore>() { }
        and:
        def data = new LazyDigestStore(new DataContentReader('FOO'.bytes.encodeBase64().toString()), 'media', '12345', 1000)

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
        copy.size == 1000
    }

    def 'should encode and decode gzip content reader' () {
        given:
        def encoder = new MoshiEncodeStrategy<DigestStore>() { }
        and:
        def data = new LazyDigestStore(
                GzipContentReader.fromPlainString('Hello world'),
                'text/json',
                '12345',
                2000 )

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
                '12345',
                'from foo',
                'conda spec',
                'spack spec',
                Path.of("/some/path"),
                'docker.io/some:image:12345',
                PlatformId.NULL,
                ContainerPlatform.of('linux/amd64'),
                'cacherepo',
                "1.2.3.4",
                '{"config":"json"}',
                null,
                null,
                'scan12345',
                context,
                null)
                .withBuildId('1')
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
}
