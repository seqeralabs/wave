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

package io.seqera.wave.service.blob.impl

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import io.micronaut.context.ApplicationContext
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.core.RoutePath
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.service.blob.BlobCacheInfo
import io.seqera.wave.service.blob.BlobStore
import io.seqera.wave.service.job.JobEvent
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.job.spec.TransferJobSpec
import io.seqera.wave.test.AwsS3TestContainer
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BlobCacheServiceImplTest2 extends Specification implements AwsS3TestContainer {

    @Unroll
    def 'should create service' () {
        given:
        String testEndpoint = "https://${getAwsS3HostName()}:${getAwsS3Port()}"
        and:
        def PROPS = [
                'wave.blobCache.enabled': 'true',
                'wave.blobCache.signing-strategy': 'aws-presigned-url',
                'wave.blobCache.storage.bucket': BUCKET,
                'wave.blobCache.baseUrl': BASE_URL,
                'wave.blobCache.storage.region': 'eu-west-1',
                'wave.blobCache.storage.endpoint': testEndpoint,
                'wave.blobCache.storage.accessKey': 'accessKey',
                'wave.blobCache.storage.secretKey': 'secretKey'
        ]
        def ctx = ApplicationContext.run(PROPS)
        def service = ctx.getBean(BlobCacheServiceImpl)
        def route = RoutePath.v2manifestPath(ContainerCoordinates.parse(IMAGE))

        when:
        def downloadUrl = service.blobDownloadUri(route)
        then:
        def uri = URI.create(downloadUrl)
        uri.scheme == EX_SCHEME
        uri.host == EX_HOST
        uri.path == EX_PATH
        uri.query.contains('X-Amz-SignedHeaders=host')

        cleanup:
        ctx.close()

        where:
        IMAGE                   | BUCKET              | BASE_URL              | EX_SCHEME     | EX_HOST       | EX_PATH
        'library/ubuntu:22.04'  | 's3://foo'          | null                  | 'https'       | 'foo.localhost' | '/docker.io/v2/library/ubuntu/manifests/22.04'
        'library/ubuntu:22.04'  | 's3://foo/x/y'      | null                  | 'https'       | 'foo.localhost' | '/x/y/docker.io/v2/library/ubuntu/manifests/22.04'
        and:
        'library/ubuntu:22.04'  | 's3://foo'          | 'http://bar.com'      | 'http'        | 'bar.com'     | '/docker.io/v2/library/ubuntu/manifests/22.04'
        'library/ubuntu:22.04'  | 's3://foo'          | 'https://bar.com'     | 'https'       | 'bar.com'     | '/docker.io/v2/library/ubuntu/manifests/22.04'
        'library/ubuntu:22.04'  | 's3://foo'          | 'https://bar.com/y'   | 'https'       | 'bar.com'     | '/y/docker.io/v2/library/ubuntu/manifests/22.04'
        and:
        'library/ubuntu:22.04'  | 's3://foo'          | 'https://bar.com/y'   | 'https'       | 'bar.com'     | '/y/docker.io/v2/library/ubuntu/manifests/22.04'
        'ubuntu@sha256:32353'   | 's3://foo'          | 'https://bar.com/'    | 'https'       | 'bar.com'     | '/docker.io/v2/library/ubuntu/manifests/sha256:32353'
    }


    @Unroll
    def 'should create service' () {
        given:
        String testEndpoint = "https://${getAwsS3HostName()}:${getAwsS3Port()}"
        and:
        def PROPS = [
                'wave.blobCache.enabled': 'true',
                'wave.blobCache.signing-strategy': 'cloudflare-waf-token',
                'wave.blobCache.cloudflare.secret-key': 'foo',
                'wave.blobCache.cloudflare.lifetime': '1h',
                'wave.blobCache.storage.bucket': BUCKET,
                'wave.blobCache.baseUrl': BASE_URL,
                'wave.blobCache.storage.region': 'eu-west-1',
                'wave.blobCache.storage.endpoint': testEndpoint,
                'wave.blobCache.storage.accessKey': 'accessKey',
                'wave.blobCache.storage.secretKey': 'secretKey'
        ]
        def ctx = ApplicationContext.run(PROPS)
        def service = ctx.getBean(BlobCacheServiceImpl)
        def route = RoutePath.v2manifestPath(ContainerCoordinates.parse(IMAGE))

        when:
        def result = service.blobDownloadUri(route)
        then:
        def uri = URI.create(result)
        uri.scheme == EX_SCHEME
        uri.host == EX_HOST
        uri.path == EX_PATH
        uri.query.startsWith('verify=')

        cleanup:
        ctx.close()

        where:
        IMAGE                   | BUCKET              | BASE_URL              | EX_SCHEME     | EX_HOST       | EX_PATH
        'library/ubuntu:22.04'  | 's3://foo'          | 'http://bar.com'      | 'http'        | 'bar.com'     | '/docker.io/v2/library/ubuntu/manifests/22.04'
        'library/ubuntu:22.04'  | 's3://foo'          | 'https://bar.com'     | 'https'       | 'bar.com'     | '/docker.io/v2/library/ubuntu/manifests/22.04'
        'library/ubuntu:22.04'  | 's3://foo'          | 'https://bar.com/y'   | 'https'       | 'bar.com'     | '/y/docker.io/v2/library/ubuntu/manifests/22.04'
        and:
        'library/ubuntu:22.04'  | 's3://foo'          | 'https://bar.com/y'   | 'https'       | 'bar.com'     | '/y/docker.io/v2/library/ubuntu/manifests/22.04'
        'ubuntu@sha256:32353'   | 's3://foo'          | 'https://bar.com/'    | 'https'       | 'bar.com'     | '/docker.io/v2/library/ubuntu/manifests/sha256:32353'
    }

    def 'handle job event when blob cache entry is unknown'() {
        given:
        def blobStore = Mock(BlobStore)
        def service = new BlobCacheServiceImpl(blobStore: blobStore)
        def event = Mock(JobEvent)
        event.job >> new TransferJobSpec('unknown-id')

        when:
        service.onJobEvent(event)

        then:
        1 * blobStore.getBlob('unknown-id') >> null
    }

    def 'handle job event when blob cache entry is already completed'() {
        given:
        def blobStore = Mock(BlobStore)
        def blob = Mock(BlobCacheInfo)
        def service = new BlobCacheServiceImpl(blobStore: blobStore)
        def event = Mock(JobEvent)
        event.job >> new TransferJobSpec('completed-id')

        when:
        service.onJobEvent(event)

        then:
        1 * blobStore.getBlob('completed-id') >> blob
        1 * blob.done() >> true
    }

    def 'handle job event when job is completed'() {
        given:
        def blobStore = Mock(BlobStore)
        def blob = Mock(BlobCacheInfo)
        blob.completed(_,_) >> BlobCacheInfo.create('location', 'object', null, null)
        def config = new BlobCacheConfig(failureDuration: Duration.ofMinutes(1))
        def service = new BlobCacheServiceImpl(blobStore: blobStore, blobConfig: config)
        def event = Mock(JobEvent)
        event.job >> new TransferJobSpec('job-id')
        event.type >> JobEvent.Type.Complete
        event.state >> JobState.completed(0, 'Job completed')

        when:
        service.onJobEvent(event)

        then:
        1 * blobStore.getBlob('job-id') >> blob
        1 * blob.done() >> false
        1 * blobStore.storeBlob(_, _, _)
        1 * blob.id()
    }

    def 'handle job event when job times out'() {
        given:
        def blobStore = Mock(BlobStore)
        def blob = Mock(BlobCacheInfo)
        def config = new BlobCacheConfig(failureDuration: Duration.ofMinutes(1))
        def service = new BlobCacheServiceImpl(blobStore: blobStore, blobConfig: config)
        def event = Mock(JobEvent)
        event.job >> new TransferJobSpec('job-id')
        event.type >> JobEvent.Type.Timeout

        when:
        service.onJobEvent(event)

        then:
        1 * blobStore.getBlob('job-id') >> blob
        1 * blob.failed(_) >> blob
    }

    def 'handle job event when job encounters an error'() {
        given:
        def blobStore = Mock(BlobStore)
        def blob = Mock(BlobCacheInfo)
        def config = new BlobCacheConfig(failureDuration: Duration.ofMinutes(1))
        def service = new BlobCacheServiceImpl(blobStore: blobStore, blobConfig: config)
        def event = Mock(JobEvent)
        event.job >> new TransferJobSpec('job-id')
        event.type >> JobEvent.Type.Error
        event.error >> new RuntimeException("Error message")

        when:
        service.onJobEvent(event)

        then:
        1 * blobStore.getBlob('job-id') >> blob
        1 * blob.done() >> false
    }

}
