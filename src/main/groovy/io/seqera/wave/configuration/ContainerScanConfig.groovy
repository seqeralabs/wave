package io.seqera.wave.configuration

import java.nio.file.Files
import java.nio.file.Path
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
/**
 * Container Scan service settings
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
@Singleton
@Slf4j
class ContainerScanConfig {

    /**
     * Docker image of tool need to be used for container scanner
     */
    @Value('${wave.scan.image.name:aquasec/trivy:0.43.0}')
    private String scannerImage

    /**
     * The host path where cache DB stored
     */
    @Value('${wave.build.workspace}')
    private String buildDirectory

    String getScannerImage() {
        return scannerImage
    }

    @Memoized
    Path getCacheDirectory() {
        final result = Path.of(buildDirectory).toAbsolutePath().resolve('.trivy-cache')
        Files.createDirectories(result)
        return result
    }

    @PostConstruct
    private void init() {
        log.debug("Scanner config: docker image name: ${scannerImage}; cache directory: ${cacheDirectory}")
    }
}
