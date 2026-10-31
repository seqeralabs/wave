/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
/**
 * Model SOCI index service config options
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@ToString(includeNames = true, includePackage = false)
@Requires(bean = SociEnabled)
@Singleton
@CompileStatic
class SociConfig {

    /**
     * The zTOC span size, i.e. the distance between two decompression checkpoints. Same as soci
     */
    final long spanSize = 4194304

    /**
     * Layers smaller than this size are not indexed. Same default as soci
     */
    @Value('${wave.soci.min-layer-size:10485760}')
    long minLayerSize

    /**
     * How long the request of a container manifest is held while its layers are indexed. When the
     * indexing does not complete in time the container is served without SOCI index
     */
    @Value('${wave.soci.await-timeout:5m}')
    Duration awaitTimeout

    /**
     * How long a zTOC lookup entry is kept in the cache. It must be shorter than the
     * lifecycle expiry of the zTOC repository
     */
    @Value('${wave.soci.ztoc-cache-duration:30d}')
    Duration ztocCacheDuration

    @Value('${wave.soci.indexer-image:`public.cr.seqera.io/wave/soci-indexer:v0.1.0`}')
    String indexerImage

    @Value('${wave.soci.job-timeout:10m}')
    Duration jobTimeout

    /**
     * How long a layer whose indexing failed is not re-submitted
     */
    @Value('${wave.soci.failure-duration:10m}')
    Duration failureDuration

    /**
     * How long the publication of the SOCI-enabled tag of a durable container waits for the container
     * (build or mirror) and for its layers indexing
     */
    @Value('${wave.soci.publish-timeout:30m}')
    Duration publishTimeout

    /**
     * The OCI repository where zTOC artifacts are stored. Defaults to {@code <wave.build.repo>/soci-ztoc}
     */
    @Nullable
    @Value('${wave.soci.repo}')
    String repo

    @Nullable
    @Value('${wave.build.repo}')
    String buildRepo

    @Nullable
    @Value('${wave.soci.k8s.resources.requests.cpu}')
    String requestsCpu

    @Nullable
    @Value('${wave.soci.k8s.resources.requests.memory}')
    String requestsMemory

    @Nullable
    @Value('${wave.soci.k8s.resources.limits.cpu}')
    String limitsCpu

    @Nullable
    @Value('${wave.soci.k8s.resources.limits.memory}')
    String limitsMemory

    @PostConstruct
    void init() {
        if( !repo && buildRepo )
            repo = "${buildRepo}/soci-ztoc".toString()
        if( !repo )
            throw new IllegalStateException("Missing SOCI zTOC repository - set 'wave.soci.repo' or 'wave.build.repo'")
        log.info "Creating SOCI config - ${this}"
    }
}
