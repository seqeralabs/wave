package io.seqera.wave.service.scan

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
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
    @Override
    String scanContainer(String containerName) {
        if(isScanToolAvailable()) {
            Process process = new ProcessBuilder(scanToolName, containerName).start()
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()))
            StringBuilder processOutput = new StringBuilder();
            String outputLine;
            while((outputLine = bufferedReader.readLine())!=null){
                processOutput.append(outputLine).append("\n")
            }
            try {
                int exitCode = process.waitFor()
                if ( exitCode == 0 ) {
                    return processOutput.toString()
                } else {
                    log.warn("Container scanner failed to scan container, it exited with code : "+ exitCode)
                }
            }catch (Exception e){
                log.warn("Container scanner failed to scan container, reason : "+ e.getMessage())
            }
        }
        return null
    }
}
