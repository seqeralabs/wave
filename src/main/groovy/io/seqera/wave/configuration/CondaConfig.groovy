package io.seqera.wave.configuration

import java.time.Duration
import javax.annotation.PostConstruct

import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable

/**
 * Conda Fetcher service settings
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */

class CondaConfig {
    /**
     * Docker image of tool need to be used for conda fetcher
     */
    @Value('${wave.condafetcher.image.name:continuumio/miniconda3}')
    String condaImage

    @Value('${wave.condafetcher.k8s.resources.requests.cpu}')
    @Nullable
    String requestsCpu

    @Value('${wave.condafetcher.k8s.resources.requests.memory}')
    @Nullable
    String requestsMemory

    @Value('${wave.condafetcher.timeout:10m}')
    Duration timeout

    @PostConstruct
    private void init() {
        log.debug("Conda config: docker image name: ${condaImage}; timeout: ${timeout}; cpus: ${requestsCpu}; mem: ${requestsMemory}")
    }
}
