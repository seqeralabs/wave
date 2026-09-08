/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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
package io.seqera.wave.service.blob.impl

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.core.RoutePath
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.service.blob.BlobEntry
import io.seqera.wave.service.blob.BlobStateStore
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.test.AwsS3TestContainer

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */

class BlobCacheServiceImplTest extends Specification implements AwsS3TestContainer{

    def 'should get s5cmd cli' () {
        given:
        def service = new BlobCacheServiceImpl(blobConfig: new BlobCacheConfig(storageBucket: 's3://store/blobs/'))
        def route = RoutePath.v2manifestPath(ContainerCoordinates.parse('ubuntu@sha256:aabbcc'))

        when:
        def result = service.s5cmd(route, Mock(BlobEntry))
        then:
        result == ['s5cmd', '--json', 'pipe',  's3://store/blobs/docker.io/v2/library/ubuntu/manifests/sha256:aabbcc']

        when:
        result = service.s5cmd(route, BlobEntry.create('http://foo', 'http://bar', [:], ['Content-Type':['foo'], 'Cache-Control': ['bar']]))
        then:
        result == ['s5cmd', '--json', 'pipe', '--content-type', 'foo', '--cache-control', 'bar', 's3://store/blobs/docker.io/v2/library/ubuntu/manifests/sha256:aabbcc']

    }

    def 'should get s5cmd cli with custom endpoint' () {
        given:
        def config = new BlobCacheConfig( storageBucket: 's3://store/blobs/', storageEndpoint: 'https://foo.com' )
        def service = new BlobCacheServiceImpl(blobConfig: config)
        def route = RoutePath.v2manifestPath(ContainerCoordinates.parse('ubuntu@sha256:aabbcc'))

        when:
        def result = service.s5cmd(route, new BlobEntry())
        then:
        result == ['s5cmd', '--endpoint-url', 'https://foo.com', '--json', 'pipe', 's3://store/blobs/docker.io/v2/library/ubuntu/manifests/sha256:aabbcc']
    }

    def 'should get transfer command' () {
        given:
        def proxyService = Mock(RegistryProxyService)
        def service = new BlobCacheServiceImpl( blobConfig: new BlobCacheConfig(storageBucket: 's3://store/blobs/'), proxyService: proxyService )
        def route = RoutePath.v2manifestPath(ContainerCoordinates.parse('ubuntu@sha256:aabbcc'))
        and:
        def response = ['content-type': ['something']]
        def blobCache = BlobEntry.create('http://foo','http://bar', ['foo': ['one']], response)
        
        when:
        def result = service.transferCommand(route, blobCache)
        then:
        proxyService.curl(route, [foo:'one']) >> ['curl', '-X', 'GET', 'http://foo']
        and:
        result == [
                'bash',
                '-c',
                "set -o pipefail; curl -X GET 'http://foo' | s5cmd --json pipe --content-type something 's3://store/blobs/docker.io/v2/library/ubuntu/manifests/sha256:aabbcc'"
        ]
    }

    private static BlobEntry blobEntry(String objectUri, Long contentLength) {
        final response = contentLength!=null
                ? ['Content-Length': [String.valueOf(contentLength)]]
                : [:] as Map<String,List<String>>
        return BlobEntry.create('http://foo', objectUri, [:], response)
    }

    def 'should validate the transferred blob size' () {
        given:
        def OBJECT = 's3://store/blobs/foo'
        def service = Spy(BlobCacheServiceImpl)
        def entry = blobEntry(OBJECT, LENGTH)

        when:
        def result = service.checkTransferredSize(entry)
        then:
        1 * service.blobSize(OBJECT) >> UPLOADED
        and:
        (result != null) == ERROR

        where:
        LENGTH  | UPLOADED  | ERROR
        100L    | 100L      | false
        100L    | 0L        | true
        100L    | 50L       | true
        100L    | null      | true
        null    | 100L      | false
        null    | 0L        | true
        null    | null      | true
    }

    def 'should error the blob entry when the uploaded object is empty' () {
        given:
        def OBJECT = 's3://store/blobs/foo'
        def blobStore = Mock(BlobStateStore)
        def service = Spy(new BlobCacheServiceImpl(blobStore: blobStore))
        def entry = blobEntry(OBJECT, 100L)
        def job = JobSpec.transfer('1', 'operation-1', Instant.now(), Duration.ofMinutes(1))

        when:
        service.onJobCompletion(job, entry, JobState.succeeded('some logs'))
        then:
        1 * service.blobSize(OBJECT) >> 0L
        and:
        // the invalid object is removed so it is not served as a valid cache entry
        1 * service.deleteBlob(OBJECT) >> null
        and:
        1 * blobStore.storeBlob(OBJECT, { BlobEntry it -> it.state==BlobEntry.State.ERRORED && !it.succeeded() })
    }

    def 'should complete the blob entry when the uploaded object matches the content length' () {
        given:
        def OBJECT = 's3://store/blobs/foo'
        def blobStore = Mock(BlobStateStore)
        def service = Spy(new BlobCacheServiceImpl(blobStore: blobStore))
        def entry = blobEntry(OBJECT, 100L)
        def job = JobSpec.transfer('1', 'operation-1', Instant.now(), Duration.ofMinutes(1))

        when:
        service.onJobCompletion(job, entry, JobState.succeeded('some logs'))
        then:
        1 * service.blobSize(OBJECT) >> 100L
        and:
        0 * service.deleteBlob(_) >> null
        and:
        1 * blobStore.storeBlob(OBJECT, { BlobEntry it -> it.state==BlobEntry.State.COMPLETED && it.succeeded() })
    }

    def 'should not validate the object when the transfer job failed' () {
        given:
        def OBJECT = 's3://store/blobs/foo'
        def blobStore = Mock(BlobStateStore)
        def service = Spy(new BlobCacheServiceImpl(blobStore: blobStore))
        def entry = blobEntry(OBJECT, 100L)
        def job = JobSpec.transfer('1', 'operation-1', Instant.now(), Duration.ofMinutes(1))

        when:
        service.onJobCompletion(job, entry, JobState.failed(1, 'curl failed'))
        then:
        0 * service.blobSize(_) >> null
        and:
        // a failed pipeline can still have uploaded a partial object, remove it
        1 * service.deleteBlob(OBJECT) >> null
        and:
        1 * blobStore.storeBlob(OBJECT, { BlobEntry it -> it.state==BlobEntry.State.ERRORED && it.logs=='curl failed' })
    }

}
