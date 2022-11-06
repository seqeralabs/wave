package io.seqera.wave.service.builder

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value

import static io.seqera.wave.WaveDefault.BUILD_CONTEXT_PATH

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
        final result = new ArrayList(10)
        result
                << "--dockerfile"
                << "${BUILD_CONTEXT_PATH}/Dockerfile".toString()
                << '--context'
                << BUILD_CONTEXT_PATH
                << "--destination"
                << req.targetImage
                << "--cache=true"
                << "--custom-platform"
                << req.platform.toString()

        if( req.cacheRepository ) {
            result << "--cache-repo" << req.cacheRepository
        }

        if( !compressCaching )
            result << "--compressed-caching" << 'false'

        return result
    }

}
