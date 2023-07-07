package io.seqera.wave.service.scan

import spock.lang.Specification

class ContainerScanStrategyTest extends Specification {
    def "should return trivy command"() {
        given:
        def targetImage = "respository/scantool"
        def containerScanStrategy = Spy(ContainerScanStrategy)
        when:
        def command = containerScanStrategy.trivyWrapper(targetImage)
        then:
        command == ['--format',
                        'json',
                        'image',
                        targetImage]
    }
}
