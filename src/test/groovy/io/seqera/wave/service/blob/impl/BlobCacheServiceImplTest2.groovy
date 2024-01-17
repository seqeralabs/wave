package io.seqera.wave.service.blob.impl

import spock.lang.Specification
import spock.lang.Unroll

import io.micronaut.context.ApplicationContext
import io.seqera.wave.core.RoutePath
import io.seqera.wave.model.ContainerCoordinates

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BlobCacheServiceImplTest2 extends Specification {

    @Unroll
    def 'should create service' () {
        given:
        def PROPS = [
                'wave.blobCache.enabled': 'true',
                'wave.blobCache.storage.bucket': BUCKET,
                'wave.blobCache.baseUrl': BASE_URL
        ]
        def ctx = ApplicationContext.run(PROPS)
        def service = ctx.getBean(BlobCacheServiceImpl)
        def route = RoutePath.v2manifestPath(ContainerCoordinates.parse('library/ubuntu:22.04'))

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
        BUCKET              | BASE_URL              | EX_SCHEME     | EX_HOST       | EX_PATH
        's3://foo'          | null                  | 'https'       | 'foo.localhost' | '/docker.io/v2/library/ubuntu/manifests/22.04'
        's3://foo/x/y'      | null                  | 'https'       | 'foo.localhost' | '/x/y/docker.io/v2/library/ubuntu/manifests/22.04'
        and:
        's3://foo'          | 'http://bar.com'      | 'http'        | 'bar.com'     | '/docker.io/v2/library/ubuntu/manifests/22.04'
        's3://foo'          | 'https://bar.com'     | 'https'       | 'bar.com'     | '/docker.io/v2/library/ubuntu/manifests/22.04'
        's3://foo'          | 'https://bar.com/y'   | 'https'       | 'bar.com'     | '/y/docker.io/v2/library/ubuntu/manifests/22.04'

    }

}
