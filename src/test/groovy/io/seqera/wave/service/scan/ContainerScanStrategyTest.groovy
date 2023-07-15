package io.seqera.wave.service.scan

import spock.lang.Specification

import java.nio.file.Path
import java.time.Duration

import io.seqera.wave.configuration.ScanConfig

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
        def config = Mock(ScanConfig) { getTimeout() >> Duration.ofMinutes(100) }

        when:
        def command = containerScanStrategy.scanCommand(targetImage, outFile, config)
        then:
        command == [ '--quiet',
                     'image',
                     '--timeout',
                     '100m',
                     '--format',
                     'json',
                     '--output',
                     '/some/out.json',
                     targetImage]
    }
}
