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

package io.seqera.wave.service.builder

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.kubernetes.client.openapi.ApiException
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.configuration.SpackConfig
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.k8s.K8sService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import static io.seqera.wave.util.K8sHelper.getSelectorLabel
/**
 * Build a container image using running a K8s pod
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Primary
@Requires(property = 'wave.build.k8s')
@Singleton
@CompileStatic
class KubeBuildStrategy extends BuildStrategy {

    @Property(name='wave.build.k8s.node-selector')
    @Nullable
    private Map<String, String> nodeSelectorMap

    @Inject
    private K8sService k8sService

    @Inject
    private BuildConfig buildConfig

    @Inject
    private SpackConfig spackConfig

    @Inject
    private RegistryProxyService proxyService

    protected String podName(BuildRequest req) {
        return "build-${req.buildId}".toString().replace('_', '-')
    }

    @Override
    BuildResult build(BuildRequest req) {
        try {
            final buildImage = getBuildImage(req)
            final buildCmd = launchCmd(req)
            final name = podName(req)
            final timeout = req.maxDuration ?: buildConfig.defaultTimeout
            final selector= getSelectorLabel(req.platform, nodeSelectorMap)
            final pod = k8sService.buildContainer(name, buildImage, buildCmd, req.s3Key, req.configJson, timeout, selector)
            final exitCode = k8sService.waitPodCompletion(pod, timeout.toMillis())
            final stdout = k8sService.logsPod(pod)
            if( exitCode!=null ) {
                final digest = exitCode==0 ? proxyService.getImageDigest(req, true) : null
                return BuildResult.completed(req.buildId, exitCode, stdout, req.startTime, digest)
            }
            else {
                return BuildResult.failed(req.buildId, stdout, req.startTime)
            }
        }
        catch (ApiException e) {
            throw new BadRequestException("Unexpected build failure - ${e.responseBody}", e)
        }
    }

    protected String getBuildImage(BuildRequest buildRequest){
        if( buildRequest.formatDocker() ) {
            return buildConfig.buildkitImage
        }

        if( buildRequest.formatSingularity() ) {
            return buildConfig.singularityImage(buildRequest.platform)
        }

        throw new IllegalArgumentException("Unexpected container platform: ${buildRequest.platform}")
    }

    @Override
    void cleanup(BuildRequest req) {
        super.cleanup(req)
        final name = podName(req)
        try {
            k8sService.deletePod(name)
        }
        catch (Exception e) {
            log.warn ("Unable to delete pod=$name - cause: ${e.message ?: e}", e)
        }
    }

}
