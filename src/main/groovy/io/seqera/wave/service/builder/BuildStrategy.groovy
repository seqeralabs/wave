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
import io.seqera.wave.configuration.BuildConfig
import jakarta.inject.Inject
/**
 * Defines an abstract container build strategy.
 *
 * Specialization can support different build backends
 * such as Docker and Kubernetes.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
abstract class BuildStrategy {

    @Inject
    private BuildConfig buildConfig

    abstract BuildResult build(BuildRequest req)

    void cleanup(BuildRequest req) {
        req.workDir?.deleteDir()
    }

    List<String> launchCmd(BuildRequest req) {
        if(req.formatDocker()) {
            dockerLaunchCmd(req)
        }
        else if(req.formatSingularity()) {
            singularityLaunchCmd(req)
        }
        else
            throw new IllegalStateException("Unknown build format: $req.format")
    }

    protected List<String> dockerLaunchCmd(BuildRequest req) {
        final result = new ArrayList(10)
        result
                << "--dockerfile"
                << "$req.workDir/Containerfile".toString()
                << "--context"
                << "$req.workDir/context".toString()
                << "--destination"
                << req.targetImage
                << "--cache=true"
                << "--custom-platform"
                << req.platform.toString()

        if( req.cacheRepository ) {
            result << "--cache-repo" << req.cacheRepository
        }

        if( !buildConfig.compressCaching ){
            result << "--compressed-caching=false"
        }

        if(req.spackFile){
            result << '--build-arg'
            result << 'AWS_STS_REGIONAL_ENDPOINTS=$(AWS_STS_REGIONAL_ENDPOINTS)'
            result << '--build-arg'
            result << 'AWS_REGION=$(AWS_REGION)'
            result << '--build-arg'
            result << 'AWS_DEFAULT_REGION=$(AWS_DEFAULT_REGION)'
            result << '--build-arg'
            result << 'AWS_ROLE_ARN=$(AWS_ROLE_ARN)'
            result << '--build-arg'
            result << 'AWS_WEB_IDENTITY_TOKEN_FILE=$(AWS_WEB_IDENTITY_TOKEN_FILE)'
        }

        if(req.labels){
            req.labels.forEach { key, value ->
                if (key.contains(' ') || value.contains(' ')) {
                    result << "--label \"$key\"=\"$value\""
                } else {
                    result << "--label $key=\"$value\""
                }
            }
        }

        return result
    }

    protected List<String> singularityLaunchCmd(BuildRequest req) {
        final result = new ArrayList(10)
        result
            << 'sh'
            << '-c'
            << "singularity build image.sif ${req.workDir}/Containerfile && singularity push image.sif ${req.targetImage}".toString()
        return result
    }
}
