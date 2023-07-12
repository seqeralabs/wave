package io.seqera.wave.service.scan

import java.nio.file.Path
import java.time.Instant

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.ContainerScanConfig
import io.seqera.wave.model.ScanResult
import io.seqera.wave.service.builder.BuildRequest
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Implements ContainerScanStrategy for Docker
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@Requires(missingProperty = 'wave.build.k8s')
@CompileStatic
class DockerContainerScanStrategy extends ContainerScanStrategy {

    @Inject
    private ContainerScanConfig containerScanConfig

    DockerContainerScanStrategy(ContainerScanConfig containerScanConfig) {
        this.containerScanConfig = containerScanConfig
    }

    @Override
    ScanResult scanContainer(String scannerImage, BuildRequest buildRequest) {

        log.info("Launching container scan for buildId: ${buildRequest.id}")
        final startTime = Instant.now()

        try {
            Path configFile = null
            if( buildRequest.configJson ) {
                configFile = buildRequest.workDir.resolve('config.json')
            }
            final reportFile = buildRequest.workDir.resolve(Trivy.OUTPUT_FILE_NAME)
            final dockerCommand = dockerWrapper(buildRequest.workDir, configFile)
            final trivyCommand = List.of(scannerImage) + scanCommand(buildRequest.targetImage, reportFile)
            final command = dockerCommand + trivyCommand

            //launch scanning
            log.debug("Container Scan Command: ${command.join(' ')}")
            final process = new ProcessBuilder()
                    .command(command)
                    .redirectErrorStream(true)
                    .start()

            final exitCode = process.waitFor()
            if ( exitCode != 0 ) {
                log.warn("Container scan failed to scan container, it exited with code: ${exitCode} - cause: ${process.text}")
                return ScanResult.failure(buildRequest.id, startTime, null)
            }
            else{
                log.info("Container scan completed for buildId: ${buildRequest.id}")
                return ScanResult.success(buildRequest.id, startTime, TrivyResultProcessor.process(reportFile.text))
            }
        }
        catch (Exception e){
            log.warn("Container scan failed to scan container, reason: ${e.getMessage()}")
            return ScanResult.failure(buildRequest.id, startTime, null)
        }
    }

    protected List<String> dockerWrapper(Path workDir, Path credsFile) {

        final wrapper = ['docker','run', '--rm']
        
        // scan work dir
        wrapper.add('-w')
        wrapper.add(workDir.toString())

        wrapper.add('-v')
        wrapper.add("$workDir:$workDir:rw".toString())

        // cache directory
        wrapper.add('-v')
        wrapper.add("${containerScanConfig.cacheDirectory}:${Trivy.CACHE_MOUNT_PATH}:rw".toString())

        if(credsFile) {
            wrapper.add('-v')
            wrapper.add("${credsFile}:${Trivy.CONFIG_MOUNT_PATH}:ro".toString())
        }


        return wrapper
    }
}
