package io.seqera.wave.service.builder

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import javax.annotation.Nullable

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.kubernetes.client.openapi.ApiException
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.configuration.SpackConfig
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.k8s.K8sService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import static io.seqera.wave.util.K8sHelper.getSelectorLabel
import static java.nio.file.StandardOpenOption.CREATE
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import static java.nio.file.StandardOpenOption.WRITE
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

    @Property(name='wave.build.k8s.node-selector')
    @Nullable
    private Map<String, String> nodeSelectorMap

    @Inject
    private SpackConfig spackConfig

    private String podName(BuildRequest req) {
        return "build-${req.job}"
    }

    @Override
    BuildResult build(BuildRequest req) {

        Path configFile = null
        if( req.configJson ) {
            configFile = req.workDir.resolve('config.json')
            Files.write(configFile, JsonOutput.prettyPrint(req.configJson).bytes, CREATE, WRITE, TRUNCATE_EXISTING)
        }

        try {
            final buildCmd = launchCmd(req)
            final name = podName(req)
            final selector= getSelectorLabel(req.platform, nodeSelectorMap)
            final spackCfg0 = req.isSpackBuild ? spackConfig : null
            final pod = k8sService.buildContainer(name, buildImage, buildCmd, req.workDir, configFile, spackCfg0, selector)
            final terminated = k8sService.waitPod(pod, buildTimeout.toMillis())
            final stdout = k8sService.logsPod(name)
            if( terminated ) {
                return BuildResult.completed(req.id, terminated.exitCode, stdout, req.startTime )
            }
            else {
                return BuildResult.completed(req.id, -1, stdout, req.startTime )
            }
        }
        catch (ApiException e) {
            throw new BadRequestException("Unexpected build failure - ${e.responseBody}", e)
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
