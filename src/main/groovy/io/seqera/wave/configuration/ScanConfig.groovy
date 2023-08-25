package io.seqera.wave.configuration

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import javax.annotation.Nullable
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
class ScanConfig {

    /**
     * Docker image of tool need to be used for container scanner
     */
    @Value('${wave.scan.image.name:aquasec/trivy:0.43.0}')
    private String scanImage

    @Value('${wave.scan.k8s.resources.requests.cpu}')
    @Nullable
    private String requestsCpu

    @Value('${wave.scan.k8s.resources.requests.memory}')
    @Nullable
    private String requestsMemory

    /**
     * The host path where cache DB stored
     */
    @Value('${wave.build.workspace}')
    private String buildDirectory

    @Value('${wave.scan.timeout:10m}')
    private Duration timeout

    @Value('${wave.scan.severity}')
    @Nullable
    private String severity

    String getScanImage() {
        return scanImage
    }

    @Memoized
    Path getCacheDirectory() {
        final result = Path.of(buildDirectory).toAbsolutePath().resolve('.trivy-cache')
        Files.createDirectories(result)
        return result
    }

    String getRequestsCpu() {
        return requestsCpu
    }

    String getRequestsMemory() {
        return requestsMemory
    }

    Duration getTimeout() {
        return timeout
    }

    String getSeverity() {
        return severity
    }

    @PostConstruct
    private void init() {
        log.debug("Scanner config: docker image name: ${scanImage}; cache directory: ${cacheDirectory}; timeout=${timeout}; cpus: ${requestsCpu}; mem: ${requestsMemory}; severity: $severity")
    }
}
