package io.seqera.wave.service.scan

import java.nio.file.Path

import groovy.util.logging.Slf4j
import io.seqera.wave.service.builder.BuildRequest
/**
 * Implements ScanStrategy for Docker
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
abstract class ScanStrategy {

    abstract ScanResult scanContainer(String containerScanner, BuildRequest buildRequest)

    protected List<String> scanCommand(String targetImage, Path outputFile){
        return List.of(
                '--quiet',
                'image',
                '--timeout',
                '10m',
                '--format',
                'json',
                '--output',
                outputFile.toString(),
                targetImage)
    }
}
