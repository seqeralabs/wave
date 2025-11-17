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
import io.seqera.wave.util.BucketTokenizer
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

    abstract List<String> singularityLaunchCmd(BuildRequest req)

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
            result << cacheExportOpts(req, buildConfig)
            result << "--import-cache"
            result << cacheImportOpts(req, buildConfig)
        }

        return result
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
        return result.toString()
    }

    static protected String cacheExportOpts(BuildRequest req, BuildConfig config) {
        return BuildConfig.isBucketPath(req.cacheRepository)
                ? s3ExportCacheOpts(req, config)
                : registryExportCacheOpts(req, config)
    }

    static protected String cacheImportOpts(BuildRequest req, BuildConfig config) {
        return BuildConfig.isBucketPath(req.cacheRepository)
                ? s3ImportCacheOpts(req, config)
                : registryImportCacheOpts(req, config)
    }

    static protected String registryExportCacheOpts(BuildRequest req, BuildConfig config) {
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

    static protected String registryImportCacheOpts(BuildRequest req, BuildConfig config) {
        return "type=registry,ref=${req.cacheRepository}:${req.containerId}".toString()
    }

    static protected String s3ExportCacheOpts(BuildRequest req, BuildConfig config) {
        final bucket = parseBucketFromS3Path(req.cacheRepository)
        final prefix = parsePrefixFromS3Path(req.cacheRepository)
        final region = config.getCacheBucketRegion()
        final uploadParallelism = config.cacheBucketUploadParallelism

        final result = new StringBuilder()
        result << "type=s3"
        if (region) {
            result << ",region=${region}"
        }
        result << ",bucket=${bucket}"
        if (prefix) {
            result << ",prefix=${prefix}"
        }
        result << ",name=${req.containerId}"
        result << ",mode=max"
        result << ",ignore-error=true"
        if (uploadParallelism) {
            result << ",upload_parallelism=${uploadParallelism}"
        }
        result << compressOpts(req, config)
        return result.toString()
    }

    static protected String s3ImportCacheOpts(BuildRequest req, BuildConfig config) {
        final bucket = parseBucketFromS3Path(req.cacheRepository)
        final prefix = parsePrefixFromS3Path(req.cacheRepository)
        final region = config.getCacheBucketRegion()

        final result = new StringBuilder()
        result << "type=s3"
        if (region) {
            result << ",region=${region}"
        }
        result << ",bucket=${bucket}"
        if (prefix) {
            result << ",prefix=${prefix}"
        }
        result << ",name=${req.containerId}"
        return result.toString()
    }

    private static String parseBucketFromS3Path(String s3Path) {
        final tokenizer = BucketTokenizer.from(s3Path)
        return tokenizer.bucket
    }

    private static String parsePrefixFromS3Path(String s3Path) {
        final tokenizer = BucketTokenizer.from(s3Path)
        final prefix = tokenizer.key
        // return null if prefix is null or empty
        if( !prefix )
            return null
        // ensure prefix always has a trailing slash
        return prefix.endsWith('/') ? prefix : prefix + '/'
    }

}
