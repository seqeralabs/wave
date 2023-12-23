package io.seqera.wave.service.blob

import spock.lang.Specification

import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.core.RoutePath
import io.seqera.wave.model.ContainerCoordinates
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BlobCacheServiceImplTest extends Specification {


    def 'should get s5cmd cli' () {
        given:
        def service = new BlobCacheServiceImpl(blobConfig: new BlobCacheConfig(storageBucket: 's3://store/blobs/'))
        def route = RoutePath.v2manifestPath(ContainerCoordinates.parse('ubuntu@sha256:aabbcc'))

        when:
        def result = service.s5cmd(route, [:])
        then:
        result == ['s5cmd', '--json', 'pipe', '--acl', 'public-read', 's3://store/blobs/v2/library/ubuntu/manifests/sha256:aabbcc']

        when:
        result = service.s5cmd(route, ['Content-Type':['foo'], 'Cache-Control': ['bar']])
        then:
        result == ['s5cmd', '--json', 'pipe', '--acl', 'public-read', '--content-type', 'foo', '--cache-control', 'bar', 's3://store/blobs/v2/library/ubuntu/manifests/sha256:aabbcc']

    }


    def 'should get transfer command' () {
        given:
        def proxyService = Mock(RegistryProxyService)
        def service = new BlobCacheServiceImpl( blobConfig: new BlobCacheConfig(storageBucket: 's3://store/blobs/'), proxyService: proxyService )
        def route = RoutePath.v2manifestPath(ContainerCoordinates.parse('ubuntu@sha256:aabbcc'))

        when:
        def result = service.transferCommand(route, [:])
        then:
        proxyService.curl(route, [:]) >> ['curl', '-X', 'GET', 'http://foo']
        and:
        result == [
                'sh',
                '-c',
                "curl -X GET 'http://foo' | s5cmd --json pipe --acl public-read 's3://store/blobs/v2/library/ubuntu/manifests/sha256:aabbcc'"
        ]
    }
}
