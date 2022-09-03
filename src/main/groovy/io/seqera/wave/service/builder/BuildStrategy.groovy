package io.seqera.wave.service.builder

import groovy.transform.CompileStatic
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

    abstract BuildResult build(BuildRequest req, String creds)

    void cleanup(BuildRequest req) {
        req.workDir?.deleteDir()
    }

    List<String> launchCmd(BuildRequest req) {
        final result = new ArrayList(10)
        result
                << "--dockerfile"
                << "$req.workDir/Dockerfile".toString()
                << '--context'
                << req.workDir.toString()
                << "--destination"
                << req.targetImage
                << "--cache=true"
                << "--cache-repo"
                << req.cacheRepository
    }

}
