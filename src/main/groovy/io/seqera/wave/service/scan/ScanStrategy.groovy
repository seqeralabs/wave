package io.seqera.wave.service.scan

import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.configuration.ScanConfig
/**
 * Implements ScanStrategy for Docker
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@CompileStatic
abstract class ScanStrategy {

    abstract ScanResult scanContainer(ScanRequest request)

    protected List<String> scanCommand(String targetImage, Path outputFile, ScanConfig config) {
        def cmd = ['--quiet',
                'image',
                '--timeout',
                "${config.timeout.toMinutes()}m".toString(),
                '--format',
                'json',
                '--output',
                outputFile.toString()]

        if( config.severity ) {
            cmd << '--severity'
            cmd << config.severity
        }
        cmd << targetImage
        return cmd
    }
}
