package io.seqera.wave.service.scan

import spock.lang.Specification

import java.nio.file.Path

/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class ContainerScanStrategyTest extends Specification {

    def "should return trivy command"() {
        given:
        def targetImage = "respository/scantool"
        def containerScanStrategy = Spy(ScanStrategy)
        def outFile = Path.of('/some/out.json')
        when:
        def command = containerScanStrategy.scanCommand(targetImage, outFile)
        then:
        command == [ '--quiet',
                     'image',
                     '--format',
                     'json',
                     '--output',
                     '/some/out.json',
                     targetImage]
    }
}
