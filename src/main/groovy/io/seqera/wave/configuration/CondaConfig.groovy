/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.configuration

import java.time.Duration
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton

/**
 * Conda Fetcher service settings
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
@Singleton
@Slf4j
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

    @Value('${wave.condafetcher.channels:seqera}')
    List<String> channels

    @PostConstruct
    private void init() {
        log.debug("Conda config: docker image name: ${condaImage}; timeout: ${timeout}; cpus: ${requestsCpu}; mem: ${requestsMemory}")
    }
}
