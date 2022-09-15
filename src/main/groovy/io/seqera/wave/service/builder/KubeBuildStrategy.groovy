package io.seqera.wave.service.builder

import java.time.Duration
import java.time.Instant

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.service.k8s.K8sService
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Build a container image using running a K8s job
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Primary
@Requires(property = 'wave.build.k8s')
@Singleton
@CompileStatic
class KubeBuildStrategy extends BuildStrategy {

    @Inject
    K8sService k8sService

    @Value('${wave.build.image}')
    String buildImage

    @Value('${wave.build.timeout:5m}')
    Duration buildTimeout

    private String podName(BuildRequest req) {
        return "build-${req.job}"
    }

    @Override
    BuildResult build(BuildRequest req, String creds) {

        final buildCmd = launchCmd(req)
        final name = podName(req)
        final pod = k8sService.buildContainer(name, buildImage, buildCmd, req.workDir, creds)
        final terminated = k8sService.waitPod(pod, buildTimeout.toMillis())
        final stdout = k8sService.logsPod(name)
        if( terminated ) {
            return new BuildResult(req.id, terminated.exitCode, stdout, req.startTime )
        }
        else {
            return new BuildResult(req.id, -1, stdout, req.startTime )
        }
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
