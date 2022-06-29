package io.seqera.model

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ContainerCoordinatesTest extends Specification {

    def 'should parse image name' () {
        expect:
        ContainerCoordinates.parse('busybox') == new ContainerCoordinates(null, 'library/busybox', 'latest')
        ContainerCoordinates.parse('busybox:123') == new ContainerCoordinates(null, 'library/busybox', '123')
        ContainerCoordinates.parse('foo/busybox:bar') == new ContainerCoordinates(null, 'foo/busybox', 'bar')
        ContainerCoordinates.parse('docker.io/busybox') == new ContainerCoordinates('docker.io', 'busybox', 'latest')
        ContainerCoordinates.parse('quay.io/busybox') == new ContainerCoordinates('quay.io', 'busybox', 'latest')
        ContainerCoordinates.parse('quay.io/a/b/c') == new ContainerCoordinates('quay.io', 'a/b/c', 'latest')
        ContainerCoordinates.parse('quay.io/a/b/c:v1.1') == new ContainerCoordinates('quay.io', 'a/b/c', 'v1.1')

        and:
        ContainerCoordinates.parse('canonical/ubuntu@sha256:12345') == new ContainerCoordinates(null, 'canonical/ubuntu', 'sha256:12345')

        and:
        ContainerCoordinates.parse('fedora/httpd:version1.0') == new ContainerCoordinates(null, 'fedora/httpd', 'version1.0')
        ContainerCoordinates.parse('myregistryhost:5000/fedora/httpd:version1.0') == new ContainerCoordinates('myregistryhost:5000', 'fedora/httpd', 'version1.0')
    }
}
