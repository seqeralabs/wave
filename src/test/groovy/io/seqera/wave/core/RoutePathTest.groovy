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

package io.seqera.wave.core

import spock.lang.Specification

import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User

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
        route.targetPath == PATH

        where:
        TYPE    | REG         | IMAGE     | REF             | REPO                | TARGET                         | IMAGE_AND_TAG              | PATH
        'blobs' | null        | 'busybox' | 'latest'        | 'docker.io/busybox' | 'docker.io/busybox:latest'     | 'busybox:latest'           | 'docker.io/v2/busybox/blobs/latest'
        'blobs' | 'docker.io' | 'busybox' | 'latest'        | 'docker.io/busybox' | 'docker.io/busybox:latest'     | 'busybox:latest'           | 'docker.io/v2/busybox/blobs/latest'
        'blobs' | 'quay.io'   | 'busybox' | 'v1'            | 'quay.io/busybox'   | 'quay.io/busybox:v1'           | 'busybox:v1'               | 'quay.io/v2/busybox/blobs/v1'
        'blobs' | 'quay.io'   | 'busybox' | 'sha256:123abc' | 'quay.io/busybox'   | 'quay.io/busybox@sha256:123abc'| 'busybox@sha256:123abc'    | 'quay.io/v2/busybox/blobs/sha256:123abc'

    }

    def 'should get manifest path'() {
        expect:
        RoutePath.v2manifestPath(ContainerCoordinates.parse(CONTAINER)).path == PATH

        where:
        CONTAINER              | PATH
        'ubuntu'               | '/v2/library/ubuntu/manifests/latest'
        'quay.io/foo/bar:v1.0' | '/v2/foo/bar/manifests/v1.0'
    }

    def 'should parse location' () {
        expect:
        RoutePath.parse(GIVEN) == RoutePath.v2path(TYPE, REG, IMAGE, REF)

        where:
        GIVEN                                                   | TYPE          | REG           | IMAGE         | REF
        '/v2/hello-world/manifests/latest'                      | 'manifests'   | 'docker.io'   | 'hello-world' | 'latest'
        'docker.io/v2/hello-world/manifests/latest'             | 'manifests'   | 'docker.io'   | 'hello-world' | 'latest'
        'quay.io/v2/hello-world/manifests/sha256:123456'        | 'manifests'   | 'quay.io'     | 'hello-world' | 'sha256:123456'
        and:
        '/v2/hello-world/blobs/latest'                          | 'blobs'   | 'docker.io'       | 'hello-world' | 'latest'
        'docker.io/v2/hello-world/blobs/latest'                 | 'blobs'   | 'docker.io'       | 'hello-world' | 'latest'
        'quay.io/v2/hello-world/blobs/sha256:123456'            | 'blobs'   | 'quay.io'         | 'hello-world' | 'sha256:123456'
        and:
        'foo.com:5000/v2/hello-world/blobs/latest'              | 'blobs'   | 'foo.com:5000'    | 'hello-world' | 'latest'
        'foo.com:5000/v2/hello-world/blobs/sha256:123456'       | 'blobs'   | 'foo.com:5000'    | 'hello-world' | 'sha256:123456'
        and:
        'docker://quay.io/v2/hello-world/blobs/sha256:123456'   | 'blobs'   | 'quay.io'         | 'hello-world' | 'sha256:123456'
    }

    def 'should create route path' () {
        when:
        def route1 = new RoutePath(
                   'manifests',
                 'foo.com',
                  'ubuntu',
                'latest',
                    '/v2/library/ubuntu/manifests/latest' )
        then:
        route1.type == 'manifests'
        route1.registry == 'foo.com'
        route1.image == 'ubuntu'
        route1.reference == 'latest'
        route1.path == '/v2/library/ubuntu/manifests/latest'
        route1.identity == PlatformId.NULL
        route1.request == null
        
        when:
        def route2 = new RoutePath(
                'manifests',
                'foo.com',
                'ubuntu',
                'latest',
                '/v2/library/ubuntu/manifests/latest',
                new ContainerRequestData(new PlatformId(new User(id: 100)), 'ubuntu:latest') )
        then:
        route2.type == 'manifests'
        route2.registry == 'foo.com'
        route2.image == 'ubuntu'
        route2.reference == 'latest'
        route2.path == '/v2/library/ubuntu/manifests/latest'
        route2.request.containerImage == 'ubuntu:latest'
        route2.identity == new PlatformId(new User(id: 100))
    }
}
