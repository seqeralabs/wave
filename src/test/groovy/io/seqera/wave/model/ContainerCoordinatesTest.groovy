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
        coords.repository == REPO

        where:
        STR                                             | REGISTRY              | IMAGE             | REF               | REPO                                  | TARGET
        'busybox'                                       | 'docker.io'           | 'library/busybox' | 'latest'          | 'docker.io/library/busybox'           | 'docker.io/library/busybox:latest'
        'busybox:1.2.3'                                 | 'docker.io'           | 'library/busybox' | '1.2.3'           | 'docker.io/library/busybox'           | 'docker.io/library/busybox:v1.2.3'
        'foo/busybox:bar'                               | 'docker.io'           | 'foo/busybox'     | 'bar'             | 'docker.io/foo/busybox'               | 'docker.io/foo/busybox:bar'
        'docker.io/busybox'                             | 'docker.io'           | 'busybox'         | 'latest'          | 'docker.io/busybox'                   | 'docker.io/busybox:latest'
        'quay.io/busybox'                               | 'quay.io'             | 'busybox'         | 'latest'          | 'quay.io/busybox'                     | 'quay.io/busybox:latest'
        'quay.io/a/b/c'                                 | 'quay.io'             | 'a/b/c'           | 'latest'          | 'quay.io/a/b/c'                       | 'quay.io/a/b/c:latest'
        'quay.io/a/b/c:v1.1'                            | 'quay.io'             | 'a/b/c'           | 'v1.1'            | 'quay.io/a/b/c'                       | 'quay.io/a/b/c:v1.1'
        'canonical/ubuntu@sha256:12345'                 | 'docker.io'           | 'canonical/ubuntu'| 'sha256:12345'    | 'docker.io/canonical/ubuntu'          | 'docker.io/canonical/ubuntu:12345'
        'fedora/httpd:version1.0'                       | 'docker.io'           | 'fedora/httpd'    | 'version1.0'      | 'docker.io/fedora/httpd'              | 'docker.io/fedora/httpd:version1.0'
        'myregistryhost:5000/fedora/httpd:version1.0'   | 'myregistryhost:5000' | 'fedora/httpd'    | 'version1.0'      | 'myregistryhost:5000/fedora/httpd'    | 'myregistryhost:5000/fedora/httpd:version1.0'
    }
}
