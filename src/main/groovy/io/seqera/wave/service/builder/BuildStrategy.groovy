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

    static final String BUILDKIT_ENTRYPOINT = 'buildctl-daemonless.sh'

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
                << "build"
                << "--frontend"
                << "dockerfile.v0"
                << "--local"
                << "dockerfile=$req.workDir".toString()
                << "--opt"
                << "filename=Containerfile"
                << "--local"
                << "context=$req.workDir/context".toString()
                << "--output"
                << "type=image,name=$req.targetImage,push=true".toString()
                << "--opt"
                << "platform=$req.platform".toString()

        if( req.cacheRepository ) {
            result << "--export-cache"
            def exportCache = new StringBuilder()
            exportCache << "type=registry,"
            exportCache << "image-manifest=true,"
            exportCache << "ref=${req.cacheRepository}:${req.containerId},"
            exportCache << "mode=max,"
            exportCache << "ignore-error=true,"
            exportCache << "oci-mediatypes=${buildConfig.ociMediatypes},"
            exportCache << "compression=${buildConfig.compression},"
            exportCache << "force-compression=${buildConfig.forceCompression}"
            result << exportCache.toString()

            result << "--import-cache"
            result << "type=registry,ref=$req.cacheRepository:$req.containerId".toString()
        }

        if(req.spackFile){
            result << '--opt'
            result << 'build-arg:AWS_STS_REGIONAL_ENDPOINTS=$(AWS_STS_REGIONAL_ENDPOINTS)'
            result << '--opt'
            result << 'build-arg:AWS_REGION=$(AWS_REGION)'
            result << '--opt'
            result << 'build-arg:AWS_DEFAULT_REGION=$(AWS_DEFAULT_REGION)'
            result << '--opt'
            result << 'build-arg:AWS_ROLE_ARN=$(AWS_ROLE_ARN)'
            result << '--opt'
            result << 'build-arg:AWS_WEB_IDENTITY_TOKEN_FILE=$(AWS_WEB_IDENTITY_TOKEN_FILE)'
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
