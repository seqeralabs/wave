package io.seqera.wave.service.scan

import groovy.util.logging.Slf4j

@Slf4j
abstract class ContainerScanStrategy {
    protected final String scanToolName = "trivy"
    private final String whichCommand = "which"

    abstract String scanContainer(String containerName);
    boolean isScanToolAvailable(){
        try{
            Process scanProcess = new ProcessBuilder(whichCommand, scanToolName).start()
            return scanProcess.waitFor() == 0
        }catch (Exception e){
            log.warn("There a problem with container scan tool reason: "+e.getMessage())
            return false;
        }
    }
}
