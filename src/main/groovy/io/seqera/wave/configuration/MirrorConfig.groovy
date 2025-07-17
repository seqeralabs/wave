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

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.util.BucketTokenizer
import jakarta.inject.Singleton
/**
 * Model mirror service config options
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(bean = MirrorEnabled)
@Singleton
@CompileStatic
class MirrorConfig {

    @Value('${wave.mirror.max-duration:20m}')
    Duration maxDuration

    @Value('${wave.mirror.status.delay:4s}')
    Duration statusDelay

    @Value('${wave.mirror.status.duration:1h}')
    Duration statusDuration

    @Value('${wave.mirror.failure.duration:50s}')
    Duration failureDuration

    @Value('${wave.mirror.skopeoImage}')
    String skopeoImage

    @Value('${wave.mirror.retry-attempts:2}')
    Integer retryAttempts

    @Nullable
    @Value('${wave.mirror.k8s.resources.requests.cpu}')
    String requestsCpu

    @Nullable
    @Value('${wave.mirror.k8s.resources.requests.memory}')
    String requestsMemory

    @Value('${wave.mirror.k8s.resources.limits.cpu}')
    @Nullable
    String limitsCpu

    @Value('${wave.mirror.k8s.resources.limits.memory}')
    @Nullable
    String limitsMemory

    @Value('${wave.build.workspace}')
    private String buildWorkspace

    @Memoized
    String getWorkspaceBucket() {
        if( !buildWorkspace )
            return null
        final store = BucketTokenizer.from(buildWorkspace)
        return store.scheme ? "${store.bucket}${store.path}".toString() : null
    }

    @Memoized
    String getWorkspacePrefix() {
        if( !buildWorkspace )
            return null
        final store = BucketTokenizer.from(buildWorkspace)
        return store.scheme ? store.getKey() : null
    }

}
