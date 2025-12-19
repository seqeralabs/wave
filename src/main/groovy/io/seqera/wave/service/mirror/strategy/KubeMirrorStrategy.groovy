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

package io.seqera.wave.service.mirror.strategy

import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.kubernetes.client.openapi.ApiException
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.configuration.MirrorConfig
import io.seqera.wave.configuration.MirrorEnabled
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.k8s.K8sService
import io.seqera.wave.service.mirror.MirrorRequest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import static io.seqera.wave.util.K8sHelper.getSelectorLabel

/**
 * Implements a container mirror runner based on Kubernetes
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Primary
@Requires(bean = MirrorEnabled)
@Requires(property = 'wave.build.k8s')
@Singleton
@CompileStatic
class KubeMirrorStrategy extends MirrorStrategy {

    @Inject
    private MirrorConfig config

    @Inject
    private K8sService k8sService

    @Property(name='wave.build.k8s.node-selector')
    @Nullable
    private Map<String, String> nodeSelectorMap

    @Override
    void mirrorJob(String jobName, MirrorRequest request) {
        // docker auth json file
        final Path configFile = request.authJson ? request.workDir.resolve('config.json') : null
        final selector = getSelectorLabel(request.platform, nodeSelectorMap)

        try {
            k8sService.launchMirrorJob(
                    jobName,
                    config.skopeoImage,
                    copyCommand(request),
                    request.workDir,
                    configFile,
                    config,
                    selector)
        }
        catch (ApiException e) {
            throw new BadRequestException("Unexpected build failure - ${e.responseBody}", e)
        }

    }
}
