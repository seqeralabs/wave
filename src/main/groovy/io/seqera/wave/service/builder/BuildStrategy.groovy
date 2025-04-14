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

    abstract void build(String jobName, BuildRequest req)

    static final public String BUILDKIT_ENTRYPOINT = 'buildctl-daemonless.sh'

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
                << outputOpts(req, buildConfig)
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

    static protected String outputOpts(BuildRequest req, BuildConfig config) {
        final result = new StringBuilder()
        result << "type=image"
        result << ",name=${req.targetImage}"
        result << ",push=true"
        result << ",oci-mediatypes=${config.ociMediatypes}"
        if( config.compression && config.compression != 'gzip' )
            result << ",compression=${config.compression}"
        if( config.forceCompression )
            result << ",force-compression=${config.forceCompression}"

        return result.toString()
    }

}
