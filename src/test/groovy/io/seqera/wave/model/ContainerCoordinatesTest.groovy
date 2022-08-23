package io.seqera.wave.model

import spock.lang.Specification
import spock.lang.Unroll

import io.seqera.wave.model.ContainerCoordinates
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ContainerCoordinatesTest extends Specification {

    @Unroll
    def 'should parse coordinates' () {
        when:
        def coords = ContainerCoordinates.parse(STR)
        then:
        coords.registry == REGISTRY
        coords.image == IMAGE
        coords.reference == REF

        where:
        STR                                             | REGISTRY              | IMAGE             | REF
        'busybox'                                       | null                  | 'library/busybox' | 'latest'
        'busybox:123'                                   | null                  | 'library/busybox' | '123'
        'foo/busybox:bar'                               | null                  | 'foo/busybox'     | 'bar'
        'docker.io/busybox'                             | 'docker.io'           | 'busybox'         | 'latest'
        'quay.io/busybox'                               | 'quay.io'             | 'busybox'         | 'latest'
        'quay.io/a/b/c'                                 | 'quay.io'             | 'a/b/c'           | 'latest'
        'quay.io/a/b/c:v1.1'                            | 'quay.io'             | 'a/b/c'           | 'v1.1'
        'canonical/ubuntu@sha256:12345'                 | null                  | 'canonical/ubuntu'| 'sha256:12345'
        'fedora/httpd:version1.0'                       | null                  | 'fedora/httpd'    | 'version1.0'
        'myregistryhost:5000/fedora/httpd:version1.0'   | 'myregistryhost:5000' | 'fedora/httpd'    | 'version1.0'
    }
}
