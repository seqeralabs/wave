package io.seqera.wave.service.blob.impl

import spock.lang.Specification
import spock.lang.Unroll

import io.micronaut.context.ApplicationContext
import io.seqera.wave.core.RoutePath
import io.seqera.wave.model.ContainerCoordinates
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
                'wave.blobCache.storage.endpoint': testEndpoint
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

}
