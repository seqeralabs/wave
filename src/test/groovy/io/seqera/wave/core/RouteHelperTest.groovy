package io.seqera.wave.core

import spock.lang.Specification
import spock.lang.Unroll

import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.ContainerTokenService
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RouteHelperTest extends Specification {

    @Unroll
    def 'should match manifests route #PATH'() {

        when:
        def matcher = RouteHelper.ROUTE_PATHS.matcher(PATH)
        then:
        matcher.matches() == MATCHES
        and:
        MATCHES ? matcher.group(2) == TYPE : true
        MATCHES ? matcher.group(1) == NAME : true
        MATCHES ? matcher.group(3) == REFERENCE : true

        where:
        PATH                                        | MATCHES   | NAME                  | REFERENCE     | TYPE
        '/v2/hello-world/manifests/latest'          | true      | 'hello-world'         | 'latest'      | 'manifests'
        '/v2/library/hello-world/manifests/latest'  | true      | 'library/hello-world' | 'latest'      | 'manifests'
        '/v1/library/hello-world/manifests/latest'  | false     | null                  | null          | 'manifests'
        '/v2/library/hello-world/foo/latest'        | false     | null                  | null          | 'manifests'
        '/v2/foo:bar/manifests/latest'              | false     | null                  | null          | 'manifests'
        and:
        '/v2/hello-world/blobs/latest'              | true      | 'hello-world'         | 'latest'      | 'blobs'
        '/v2/library/hello-world/blobs/latest'      | true      | 'library/hello-world' | 'latest'      | 'blobs'
        '/v1/library/hello-world/blobs/latest'      | false     | null                  | null          | 'blobs'
        '/v2/library/hello-world/foo/latest'        | false     | null                  | null          | 'blobs'
        '/v2/foo:bar/manifests/latest'              | false     | null                  | null          | 'blobs'
        and:
        '/v2/hello-world/manifests/sha256'          | true      | 'hello-world'         | 'sha256'      | 'manifests'
        '/v2/hello-world/manifests/sha256:975f4b14f326b05db86e16de00144f9c12257553bba9484fed41f9b6f2257800' | true  | 'hello-world' | 'sha256:975f4b14f326b05db86e16de00144f9c12257553bba9484fed41f9b6f2257800' | 'manifests'
        and:
        '/v2/tw/abc123/library/hello-world/blobs/latest' | true | 'abc123/library/hello-world' | 'latest' | 'blobs'
        '/v2/wt/abc123/library/hello-world/blobs/latest' | true | 'abc123/library/hello-world' | 'latest' | 'blobs'
        '/v2/wt/abc/123/library/hello-world/blobs/latest'| true | 'abc/123/library/hello-world' | 'latest' | 'blobs'
    }


    @Unroll
    def 'should get manifests route #PATH'() {
        given:
        def tokenService = Mock(ContainerTokenService)
        
        when:
        def route = new RouteHelper(tokenService).parse(PATH)
        then:
        route == ROUTE
        and:
        0 * tokenService.getRequest(_) >> null
        
        where:
        PATH                                        | ROUTE
        '/v2/hello-world/manifests/latest'          | new RoutePath('manifests', null, 'hello-world', 'latest', '/v2/hello-world/manifests/latest')
        '/v2/library/hello-world/manifests/latest'  | new RoutePath('manifests', null, 'library/hello-world', 'latest', '/v2/library/hello-world/manifests/latest')
        '/v1/library/hello-world/manifests/latest'  | RouteHelper.NOT_FOUND
        '/v2/library/hello-world/foo/latest'        | RouteHelper.NOT_FOUND
        '/v2/foo:bar/blobs/latest'                  | RouteHelper.NOT_FOUND
        and:
        '/v2/hello-world/blobs/latest'              | new RoutePath('blobs', null,'hello-world', 'latest', '/v2/hello-world/blobs/latest')
        '/v2/library/hello-world/blobs/latest'      | new RoutePath('blobs', null,'library/hello-world', 'latest', '/v2/library/hello-world/blobs/latest')
        '/v1/library/hello-world/blobs/latest'      | RouteHelper.NOT_FOUND
        '/v2/library/hello-world/foo/latest'        | RouteHelper.NOT_FOUND
        '/v2/foo:bar/blobs/latest'                  | RouteHelper.NOT_FOUND
        and:
        '/v2/tw/of2wc6jonfxs63tfpb2gm3dpo4/rnaseq-nf/manifests/v1.1'        | new RoutePath('manifests', 'quay.io', 'nextflow/rnaseq-nf', 'v1.1', '/v2/nextflow/rnaseq-nf/manifests/v1.1')
        '/v2/tw/mjuw6y3pnz2gc2lomvzhg/biocontainers/manifests/v1.2.0_cv1'   | new RoutePath('manifests', null, 'biocontainers/biocontainers', 'v1.2.0_cv1', '/v2/biocontainers/biocontainers/manifests/v1.2.0_cv1')
        and:
        '/v2/github.io/biocontainers/biocontainers/manifests/v1.1'        | new RoutePath('manifests', 'github.io', 'biocontainers/biocontainers', 'v1.1', '/v2/biocontainers/biocontainers/manifests/v1.1')
    }

    @Unroll
    def 'should find container name via token service' () {
        given:
        def tokenService = Mock(ContainerTokenService)

        when:
        def route = new RouteHelper(tokenService).parse(REQ_PATH)
        then:
        1 * tokenService.getRequest(ROUTE_TKN) >> new ContainerRequestData(null,null,REQ_IMAGE)
        and:
        route.image == ROUTE_IMAGE
        route.type == ROUTE_TYPE
        route.registry == ROUTE_REG
        route.reference == ROUTE_REF
        route.path == ROUTE_PATH

        where:
        REQ_IMAGE                                   | REQ_PATH                                                      | ROUTE_REG     | ROUTE_TKN     | ROUTE_TYPE    | ROUTE_IMAGE                       | ROUTE_REF         | ROUTE_PATH
        'ubuntu:latest'                             | '/v2/wt/a1/ubuntu/manifests/latest'                           | null          | 'a1'          | 'manifests'   | 'library/ubuntu'                  | 'latest'          | '/v2/library/ubuntu/manifests/latest'
        'canonical/ubuntu:latest'                   | '/v2/wt/b2/canonical/ubuntu/manifests/latest'                 | null          | 'b2'          | 'manifests'   | 'canonical/ubuntu'                | 'latest'          | '/v2/canonical/ubuntu/manifests/latest'
        'quay.io/canonical/ubuntu:latest'           | '/v2/wt/c3/canonical/ubuntu/manifests/latest'                 | 'quay.io'     | 'c3'          | 'manifests'   | 'canonical/ubuntu'                | 'latest'          | '/v2/canonical/ubuntu/manifests/latest'
        'biocontainers/biocontainers:v1.2.0_cv1'    | '/v2/wt/d4/biocontainers/biocontainers/blobs/v1.2.0_cv1'      | null          | 'd4'          | 'blobs'       | 'biocontainers/biocontainers'     | 'v1.2.0_cv1'      | '/v2/biocontainers/biocontainers/blobs/v1.2.0_cv1'
        'my_host:2000/canonical/ubuntu:latest'      | '/v2/wt/e5/canonical/ubuntu/blobs/latest'                     | 'my_host:2000'| 'e5'          | 'blobs'       | 'canonical/ubuntu'                | 'latest'          | '/v2/canonical/ubuntu/blobs/latest'

    }

    def 'should validate route type' () {

        when:
        def route = new RoutePath(TYPE, REG, IMAGE, REF)
        then:
        route.isManifest() == IS_MANIFEST
        route.isBlob() == IS_BLOB
        route.isTag() == IS_TAG
        route.isDigest() == IS_DIGEST


        where:
        TYPE        | REG  | IMAGE | REF           | IS_MANIFEST   | IS_BLOB   | IS_TAG    | IS_DIGEST
        'manifests' | 'io' | 'foo' | 'latest'      | true          | false     | true      | false
        'manifests' | 'io' | 'foo' | 'sha256:1234' | true          | false     | false     | true
        and:
        'blobs'     |  'io' | 'foo' | 'latest'      | false         | true      | true      | false
        'blobs'     |  'io' | 'foo' | 'sha256:1234' | false         | true      | false     | true

    }

    def 'should check target image' () {
        expect:
        new RoutePath(null, null, 'library/busybox','latest',null)
                .targetRepository == 'library/busybox:latest'
        and:
        new RoutePath(null, 'quay.io', 'library/busybox','latest',null)
                .targetRepository == 'quay.io/library/busybox:latest'
    }

}
