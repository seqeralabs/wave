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

package io.seqera.wave.service.soci.strategy

import java.nio.file.Files
import java.nio.file.Path

import groovy.transform.CompileStatic
import io.kubernetes.client.openapi.ApiException
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.configuration.SociConfig
import io.seqera.wave.configuration.SociEnabled
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.k8s.K8sService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import static io.seqera.wave.util.K8sHelper.getNoArchSelector
/**
 * Run the {@code soci-indexer} job as a Kubernetes job
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Primary
@Singleton
@Requires(bean = SociEnabled)
@Requires(property = 'wave.build.k8s')
@CompileStatic
class KubeSociIndexStrategy extends SociIndexStrategy {

    @Inject
    private SociConfig config

    @Inject
    private K8sService k8sService

    @Property(name='wave.build.k8s.node-selector')
    @Nullable
    private Map<String, String> nodeSelectorMap

    @Override
    void launchCommand(String jobName, Path workDir, List<String> command) {
        final Path configFile = workDir.resolve('config.json')
        try {
            k8sService.launchSociIndexJob(
                    jobName,
                    config.indexerImage,
                    command,
                    workDir,
                    Files.exists(configFile) ? configFile : null,
                    config,
                    getNoArchSelector(nodeSelectorMap) )
        }
        catch (ApiException e) {
            throw new BadRequestException("Unexpected SOCI index job failure - ${e.responseBody}", e)
        }
    }
}
