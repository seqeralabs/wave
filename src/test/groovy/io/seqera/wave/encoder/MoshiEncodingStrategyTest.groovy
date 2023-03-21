package io.seqera.wave.encoder

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.pairing.socket.msg.PairingHeartbeat
import io.seqera.wave.service.pairing.socket.msg.PairingResponse
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpRequest
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpResponse
import io.seqera.wave.storage.DigestStore
import io.seqera.wave.storage.LazyDigestStore
import io.seqera.wave.storage.ZippedDigestStore
import io.seqera.wave.storage.reader.DataContentReader
import io.seqera.wave.storage.reader.GzipContentReader

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
        def build = BuildResult.completed('1', 2, 'Oops', Instant.now())

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
        def data = new ContainerRequestData(1,
                2,
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

    def 'should encode and decode lazy digest store' () {
        given:
        def encoder = new MoshiEncodeStrategy<DigestStore>() { }
        and:
        def data = new LazyDigestStore(new DataContentReader('FOO'.bytes.encodeBase64().toString()), 'media', '12345')

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
    }

    def 'should encode and decode gzip content reader' () {
        given:
        def encoder = new MoshiEncodeStrategy<DigestStore>() { }
        and:
        def data = new LazyDigestStore(
                GzipContentReader.fromPlainString('Hello world'),
                'text/json',
                '12345')

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
    }

    def 'should encode and decode zipped digest store' () {
        given:
        def DATA = 'Hello wold!'
        def encoder = new MoshiEncodeStrategy<DigestStore>() { }
        and:
        def data = new ZippedDigestStore(DATA.bytes, 'my/media', '12345')

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
                bearerAuth: 'secret',
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
        copy.bearerAuth == data.bearerAuth
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


}
