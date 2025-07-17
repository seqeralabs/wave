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
import io.seqera.wave.util.FusionHelper
import jakarta.inject.Inject
import static io.seqera.wave.service.builder.BuildConstants.BUILDKIT_ENTRYPOINT
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

    abstract void build(String jobName, BuildRequest req, String key)

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
        final result = new ArrayList<String>(10)
        result
                << 'fusion cp -r'
                << "${FusionHelper.getFusionPath(buildConfig.workspaceBucket, req.buildId)} /home/user/$req.buildId &&".toString()
                << "$BUILDKIT_ENTRYPOINT build".toString()
                << '--frontend dockerfile.v0'
                << "--local dockerfile=/home/user/$req.buildId".toString()
                << '--opt filename=Containerfile'
                << "--local context=/home/user/$req.buildId/context".toString()
                << "--output ${outputOpts(req, buildConfig)}".toString()
                << "--opt platform=$req.platform".toString()

        if( req.cacheRepository ) {
            result
                << "--export-cache ${cacheOpts(req, buildConfig)} --import-cache type=registry,ref=$req.cacheRepository:$req.containerId".toString()
        }

        return List.of(result.join(" "))
    }


    static protected String compressOpts(BuildRequest req, BuildConfig config) {
        final result = new StringBuilder()
        final compression = req.compression?.mode?.toString() ?: config.compression
        final level = req.compression?.level
        final force = req.compression?.force!=null
                ? req.compression.force
                : ( config.forceCompression != null
                ? config.forceCompression
                : (compression=='estargz' ? true : null) )
        if( compression )
            result << ",compression=${compression}"
        if( level!=null )
            result << ",compression-level=${level}"
        if( force!=null )
            result << ",force-compression=${force}"
        return result.toString()
    }

    static protected String outputOpts(BuildRequest req, BuildConfig config) {
        final result = new StringBuilder()
        result << "type=image"
        result << ",name=${req.targetImage}"
        result << ",push=true"
        result << ",oci-mediatypes=${config.ociMediatypes}"
        result << compressOpts(req, config)
        return result.toString().trim()
    }

    static protected String cacheOpts(BuildRequest req, BuildConfig config) {
        final result = new StringBuilder()
        result << "type=registry"
        result << ",image-manifest=true"
        result << ",ref=${req.cacheRepository}:${req.containerId}"
        result << ",mode=max"
        result << ",ignore-error=true"
        result << ",oci-mediatypes=${config.ociMediatypes}"
        result << compressOpts(req, config)
        return result.toString()
    }

    protected String getBuildImage(BuildRequest buildRequest){
        if( buildRequest.formatDocker() ) {
            return buildConfig.buildkitImage
        }

        if( buildRequest.formatSingularity() ) {
            return buildConfig.singularityImage
        }

        throw new IllegalArgumentException("Unexpected container platform: ${buildRequest.platform}")
    }

    List<String> singularityLaunchCmd(BuildRequest req) {
        final result = new ArrayList<String>(3)
        result
                << 'sh'
                << '-c'
        final fusionCmd = new ArrayList(5)
        fusionCmd
                << 'fusion cp -r'
                << "${FusionHelper.getFusionPath(buildConfig.workspaceBucket, req.buildId)}/. /home/builder/ &&".toString()
                << 'singularity build image.sif /home/builder/Containerfile &&'
                << "singularity push image.sif ${req.targetImage}".toString()
        result.add(fusionCmd.join(' '))
        return result
    }
}
