package io.seqera.wave.configuration

import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
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
     * DOcker image of tool need to be used for container scanner
     */
    @Value('${wave.scan.image.name:aquasec/trivy:0.43.0}')
    private String scannerImage

    /**
     * The host path where cache DB stored
     */
    @Nullable
    @Value('${wave.scan.cacheDirectory}')
    private String cacheDirectory

    @Value('${wave.scan.workspace}')
    String workspace

    String getScannerImage() {
        return scannerImage
    }

    @Nullable
    String getCacheDirectory() {
        return cacheDirectory
    }

    String getWorkspace() {
        return workspace
    }

    @PostConstruct
    private void init() {
        log.debug("Scanner config : docker image name : ${scannerImage} , workspace ${workspace}, cache directory ${cacheDirectory}")
    }
}
