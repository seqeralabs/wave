package io.seqera.wave.core

import spock.lang.Specification

import io.seqera.wave.model.ContainerCoordinates

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RoutePathTest extends Specification {

    def 'should validate route type'() {

        when:
        def route = new RoutePath(TYPE, REG, IMAGE, REF)
        then:
        route.isManifest() == IS_MANIFEST
        route.isBlob() == IS_BLOB
        route.isTag() == IS_TAG
        route.isDigest() == IS_DIGEST
        route.isTagList() == IS_TAG_LIST

        where:
        TYPE        | REG  | IMAGE | REF           | IS_MANIFEST | IS_BLOB | IS_TAG | IS_DIGEST | IS_TAG_LIST
        'manifests' | 'io' | 'foo' | 'latest'      | true        | false   | true   | false     | false
        'manifests' | 'io' | 'foo' | 'sha256:1234' | true        | false   | false  | true      | false
        and:
        'blobs'     | 'io' | 'foo' | 'latest'      | false       | true    | true   | false     | false
        'blobs'     | 'io' | 'foo' | 'sha256:1234' | false       | true    | false  | true      | false
        and:
        'tags'      | 'io' | 'foo' | ''            | false       | false   | false  | false     | false
        'tags'      | 'io' | 'foo' | 'list'        | false       | false   | false  | false     | true
    }

    def 'should check target image'() {
        when:
        def route = RoutePath.v2path(TYPE, REG, IMAGE, REF)
        then:
        route.repository == REPO
        route.targetContainer == TARGET
        route.imageAndTag == IMAGE_AND_TAG

        where:
        TYPE    | REG         | IMAGE     | REF             | REPO                | TARGET                         | IMAGE_AND_TAG
        'blobs' | 'docker.io' | 'busybox' | 'latest'        | 'docker.io/busybox' | 'docker.io/busybox:latest'     | 'busybox:latest'
        'blobs' | 'quay.io'   | 'busybox' | 'v1'            | 'quay.io/busybox'   | 'quay.io/busybox:v1'           | 'busybox:v1'
        'blobs' | 'quay.io'   | 'busybox' | 'sha256:123abc' | 'quay.io/busybox'   | 'quay.io/busybox@sha256:123abc'| 'busybox@sha256:123abc'

    }

    def 'should get manifest path'() {
        expect:
        RoutePath.v2manifestPath(ContainerCoordinates.parse(CONTAINER)).path == PATH

        where:
        CONTAINER              | PATH
        'ubuntu'               | '/v2/library/ubuntu/manifests/latest'
        'quay.io/foo/bar:v1.0' | '/v2/foo/bar/manifests/v1.0'
    }

}
