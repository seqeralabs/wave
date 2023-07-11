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
class DockerContainerScanStrategy extends ContainerScanStrategy{

    @Inject
    private ContainerScanConfig containerScanConfig

    DockerContainerScanStrategy(ContainerScanConfig containerScanConfig) {
        this.containerScanConfig = containerScanConfig
    }

    @Override
    ScanResult scanContainer(String scannerImage, BuildRequest buildRequest) {

        log.info("Launching container scan for buildId: "+buildRequest.id)

        Instant startTime = Instant.now()
        StringBuilder processOutput = new StringBuilder()

        try{
            Path configFile = null
            if( buildRequest.configJson ) {
                configFile = buildRequest.workDir.resolve('config.json')
            }
            def dockerCommand = dockerWrapper(configFile)
            def trivyCommand = List.of(scannerImage)+trivyWrapper(buildRequest.targetImage)
            def command = dockerCommand+trivyCommand

            //launch scanning
            log.info("Container Scan Command: "+command.join(' '))
            Process process = new ProcessBuilder()
                    .command(command)
                    .start()

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.inputStream))
            String outputLine
            while((outputLine = bufferedReader.readLine())!=null){
                processOutput.append(outputLine)
            }
                int exitCode = process.waitFor()
                if ( exitCode != 0 ) {
                    log.warn("Container scan failed to scan container, it exited with code : ${exitCode}")
                    return ScanResult.failure(buildRequest.id, startTime, null)
                } else{
                    log.info("Container scan completed for buildId: "+buildRequest.id)
                }
        }catch (Exception e){
            log.warn("Container scan failed to scan container, reason : ${e.getMessage()}")
            return ScanResult.failure(buildRequest.id, startTime, null)
        }
        return ScanResult.success(buildRequest.id, startTime, TrivyResultProcessor.process(processOutput.toString()))
    }

    protected List<String> dockerWrapper(Path credsFile) {

        final wrapper = ['docker','run', '--rm']

        if(credsFile) {
            wrapper.add('-v')
            wrapper.add("${credsFile}:${Trivy.CONFIG_MOUNT_PATH}:ro".toString())
        }

        // cache directory
        wrapper.add('-v')
        wrapper.add("${containerScanConfig.cacheDirectory}:${Trivy.CACHE_MOUNT_PATH}:rw".toString())

        return wrapper
    }
}
