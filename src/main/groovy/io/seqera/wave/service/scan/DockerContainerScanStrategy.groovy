package io.seqera.wave.service.scan

import java.time.Instant

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.configuration.ContainerScanConfig
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Implements ContainerScanService
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class DockerContainerScanStrategy extends ContainerScanStrategy{
    @Inject
    ContainerScanConfig containerScanConfig
    @Override
    String scanContainer(String imageName) {

        log.info("Launching container scan for ${imageName}")
        String command = "docker run --rm ${containerScanConfig.scannerImage} -f json image ${imageName}"
        Process process = Runtime.getRuntime().exec(command)
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.inputStream))
        StringBuilder processOutput = new StringBuilder()
        String outputLine;
        while((outputLine = bufferedReader.readLine())!=null){
            processOutput.append(outputLine)
        }
        try {
            int exitCode = process.waitFor()
            if ( exitCode != 0 ) {
                log.warn("Container scanner failed to scan container, it exited with code : ${exitCode}")
            }

        }catch (Exception e){
            log.warn("Container scanner failed to scan container, reason : ${e.getMessage()}")
        }
        return processOutput.toString()
    }
}
