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

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
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
@Requires(property = 'wave.scan.enabled', value = 'true')
class ScanConfig {

    /**
     * Docker image of tool need to be used for container scanner
     */
    @Value('${wave.scan.image.name}')
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

    @Value('${wave.scan.timeout:15m}')
    private Duration timeout

    @Value('${wave.scan.severity}')
    @Nullable
    private String severity

    @Value('${wave.scan.retry-attempts:1}')
    int retryAttempts

    @Value('${wave.scan.status.duration:5d}')
    Duration statusDuration

    @Value('${wave.scan.failure.duration:30m}')
    Duration failureDuration

    @Value('${wave.scan.id.duration:7d}')
    Duration scanIdDuration

    @Nullable
    @Value('${wave.scan.environment}')
    List<String> environment

    @Nullable
    @Value('${wave.scan.vulnerability.limit:100}')
    Integer vulnerabilityLimit

    String getScanImage() {
        return scanImage
    }

    @Memoized
    Path getCacheDirectory() {
        final result = Path.of(buildDirectory).toAbsolutePath().resolve('.trivy-cache')
        Files.createDirectories(result)
        return result
    }

    @Memoized
    Path getWorkspace() {
        Path.of(buildDirectory).toAbsolutePath()
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

    List<Tuple2<String,String>> getEnvironmentAsTuples() {
        if( !environment )
            return List.of()
        final result = new ArrayList<Tuple2<String,String>>()
        for( String entry : environment ) {
            final p=entry.indexOf('=')
            final name = p!=-1 ? entry.substring(0,p) : entry
            final value = p!=-1 ? entry.substring(p+1) : ''
            if( !value )
                log.warn "Invalid 'wave.scan.environment value' -- offending entry: '$entry'"
            result.add(new Tuple2(name,value))
        }
        return result
    }

    @PostConstruct
    private void init() {
        log.info("Scan config: docker image name: ${scanImage}; cache directory: ${cacheDirectory}; timeout=${timeout}; cpus: ${requestsCpu}; mem: ${requestsMemory}; severity: $severity; vulnerability-limit: $vulnerabilityLimit; retry-attempts: $retryAttempts; env=${environment}")
    }
}
