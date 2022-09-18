package io.seqera.wave.core

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RoutePathTest extends Specification{

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
        when:
        def route = RoutePath.v2path(TYPE, REG, IMAGE,REF)
        then:
        route.repository == REPO
        route.targetContainer == TARGET

        where:
        TYPE    | REG           | IMAGE     | REF       | REPO                      | TARGET
        'blobs' | 'docker.io'   | 'busybox' | 'latest'  | 'docker.io/busybox'       | 'docker.io/busybox:latest'
        'blobs' | 'quay.io'     | 'busybox' | 'v1'      | 'quay.io/busybox'         | 'quay.io/busybox:v1'

    }

}
