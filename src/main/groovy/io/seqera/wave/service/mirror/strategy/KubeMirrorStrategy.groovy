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

import java.nio.file.Files
import java.nio.file.Path

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.kubernetes.client.openapi.ApiException
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.k8s.K8sService
import io.seqera.wave.service.mirror.MirrorConfig
import io.seqera.wave.service.mirror.MirrorRequest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import static java.nio.file.StandardOpenOption.CREATE
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import static java.nio.file.StandardOpenOption.WRITE

/**
 * Implements a container mirror runner based on Kubernetes
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Primary
@Requires(property = 'wave.build.k8s')
@Singleton
@CompileStatic
class KubeMirrorStrategy extends MirrorStrategy {

    @Inject
    private MirrorConfig config

    @Inject
    private K8sService k8sService

    @Override
    void mirrorJob(String jobName, MirrorRequest request) {
        Path configFile = null
        // create the work directory
        Files.createDirectories(request.workDir)
        // save docker config for creds
        if( request.authJson ) {
            configFile = request.workDir.resolve('config.json')
            Files.write(configFile, JsonOutput.prettyPrint(request.authJson).bytes, CREATE, WRITE, TRUNCATE_EXISTING)
        }

        try {
            k8sService.launchMirrorJob(
                    jobName,
                    config.skopeoImage,
                    copyCommand(request),
                    request.workDir,
                    configFile,
                    config)
        }
        catch (ApiException e) {
            throw new BadRequestException("Unexpected build failure - ${e.responseBody}", e)
        }

    }
}
