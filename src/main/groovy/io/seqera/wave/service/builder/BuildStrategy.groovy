package io.seqera.wave.service.builder

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
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

    @Value('${wave.build.compress-caching:true}')
    private Boolean compressCaching = true

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

        if( !compressCaching )
            result << "--compressed-caching=false"

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
