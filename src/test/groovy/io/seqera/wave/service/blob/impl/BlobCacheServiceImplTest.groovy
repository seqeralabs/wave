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

import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.core.RoutePath
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.service.blob.BlobCacheInfo
import io.seqera.wave.test.AwsS3TestContainer
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.presigner.S3Presigner

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
        def result = service.s5cmd(route, Mock(BlobCacheInfo))
        then:
        result == ['s5cmd', '--json', 'pipe',  's3://store/blobs/docker.io/v2/library/ubuntu/manifests/sha256:aabbcc']

        when:
        result = service.s5cmd(route, BlobCacheInfo.create('http://foo', ['Content-Type':['foo'], 'Cache-Control': ['bar']]))
        then:
        result == ['s5cmd', '--json', 'pipe', '--content-type', 'foo', '--cache-control', 'bar', 's3://store/blobs/docker.io/v2/library/ubuntu/manifests/sha256:aabbcc']

    }

    def 'should get s5cmd cli with custom endpoint' () {
        given:
        def config = new BlobCacheConfig( storageBucket: 's3://store/blobs/', storageEndpoint: 'https://foo.com' )
        def service = new BlobCacheServiceImpl(blobConfig: config)
        def route = RoutePath.v2manifestPath(ContainerCoordinates.parse('ubuntu@sha256:aabbcc'))

        when:
        def result = service.s5cmd(route, new BlobCacheInfo())
        then:
        result == ['s5cmd', '--endpoint-url', 'https://foo.com', '--json', 'pipe', 's3://store/blobs/docker.io/v2/library/ubuntu/manifests/sha256:aabbcc']
    }

    def 'should get transfer command' () {
        given:
        def proxyService = Mock(RegistryProxyService)
        def service = new BlobCacheServiceImpl( blobConfig: new BlobCacheConfig(storageBucket: 's3://store/blobs/'), proxyService: proxyService )
        def route = RoutePath.v2manifestPath(ContainerCoordinates.parse('ubuntu@sha256:aabbcc'))
        and:
        def headers = ['content-type': 'something']
        def blobCache = BlobCacheInfo.create1('http://foo', headers)
        
        when:
        def result = service.transferCommand(route, blobCache)
        then:
        proxyService.curl(route, headers) >> ['curl', '-X', 'GET', 'http://foo']
        and:
        result == [
                'sh',
                '-c',
                "curl -X GET 'http://foo' | s5cmd --json pipe --content-type something 's3://store/blobs/docker.io/v2/library/ubuntu/manifests/sha256:aabbcc'"
        ]
    }

    def 'should get pre signed download url'(){
        given:
        awsS3Container.start()
        def s3Host = getAwsS3HostName()
        def s3Port = getAwsS3Port()
        def bucketName = 'store/blobs'
        def region = 'EU-WEST-1'
        def presignedUrlParams = ['X-Amz-Algorithm', 'X-Amz-Credential', 'X-Amz-Date', 'X-Amz-Expires', 'X-Amz-Signature']

        S3Presigner s3presigner = S3Presigner.builder()
                    .region(Region.of(region))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .endpointOverride(URI.create("https://$s3Host:$s3Port"))
                    .build()

        def proxyService = Mock(RegistryProxyService)
        def service = new BlobCacheServiceImpl( blobConfig: new BlobCacheConfig(
                storageBucket: "s3://$bucketName/",
                urlSignatureDuration: Duration.ofMinutes(10),
                storageRegion: region),
                proxyService: proxyService,
                presigner: s3presigner)
        def route = RoutePath.v2manifestPath(ContainerCoordinates.parse('ubuntu@sha256:aabbcc'))

        when:
        def downloadUrl = service.blobDownloadUri(route)
        URL parsedUrl = new URL(downloadUrl)
        Map<String, String> queryParams = parsedUrl.query.split('&').collectEntries { entry ->
            def parts = entry.split('=')
            [(parts[0]): parts[1]]
        }

        then:'presigned url should contain signature'
        presignedUrlParams.every { queryParams.containsKey(it) }

        when: 'base url is provided'
        def baseUrl = 'https://something.com'
        service = new BlobCacheServiceImpl( blobConfig: new BlobCacheConfig(
                storageBucket: "s3://$bucketName/",
                urlSignatureDuration: Duration.ofMinutes(10),
                storageRegion: region,
                baseUrl: baseUrl),
                proxyService: proxyService,
                presigner: s3presigner)
        downloadUrl = service.blobDownloadUri(route)

        then: 'downloadUrl should start with base url'
        downloadUrl.startsWith(baseUrl)

        cleanup:
        awsS3Container.stop()
    }
}
