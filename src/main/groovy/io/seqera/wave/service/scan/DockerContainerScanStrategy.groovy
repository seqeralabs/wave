package io.seqera.wave.service.scan

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.ContainerScanConfig
import io.seqera.wave.model.ScanResult
import io.seqera.wave.service.builder.BuildRequest
import jakarta.inject.Singleton

import static java.nio.file.StandardOpenOption.CREATE
import static java.nio.file.StandardOpenOption.WRITE
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
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

    private final ContainerScanConfig containerScanConfig

    DockerContainerScanStrategy(ContainerScanConfig containerScanConfig) {
        this.containerScanConfig = containerScanConfig
    }

    @Override
    ScanResult scanContainer(String containerScanner, BuildRequest buildRequest) {

        log.info("Launching container scan for buildId: "+buildRequest.id)

        ScanResult scanResult = new ScanResult()
        scanResult.buildId = buildRequest.id
        scanResult.startTime = Instant.now()
        Path configFile
        try{
        if( buildRequest.configJson ) {
            Path scanDir = Files.createDirectories(Path.of(containerScanConfig.workspace))
            configFile = scanDir.resolve('config.json').toAbsolutePath()
            Files.write(configFile, JsonOutput.prettyPrint(buildRequest.configJson).bytes, CREATE, WRITE, TRUNCATE_EXISTING)
        }}catch (Exception e){
            log.warn("Error getting credentials "+e.getMessage())
        }

        def dockerCommand = dockerWrapper(configFile)
        def trivyCommand = trivyWrapper(containerScanner, buildRequest.targetImage)
        def command = dockerCommand+trivyCommand

        //launch scanning
        log.info("Container Scan Command "+command.join(' '))
        Process process = new ProcessBuilder()
                .command(command)
                .start()

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.inputStream))
        StringBuilder processOutput = new StringBuilder()
        String outputLine
        while((outputLine = bufferedReader.readLine())!=null){
            processOutput.append(outputLine)
        }
        try {
            int exitCode = process.waitFor()
            if ( exitCode != 0 ) {
                log.warn("Container scanner failed to scan container, it exited with code : ${exitCode}")
                InputStream errorStream = process.getErrorStream()
                BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream))
                String line
                while ((line = reader.readLine()) != null) {
                    log.warn(line)
                }
            } else{
                log.info("Container scan completed for buildId: "+buildRequest.id)
            }
        }catch (Exception e){
            log.warn("Container scanner failed to scan container, reason : ${e.getMessage()}")
        }

        scanResult.duration = Duration.between(scanResult.startTime,Instant.now())
        scanResult.result = processOutput.toString()

        return scanResult
    }

    private List<String> dockerWrapper(Path credsFile) {

        List<String> wrapper = ['docker',
                                'run',
                                '--rm']
        if(credsFile) {
            wrapper.add('-v')
            wrapper.add("${credsFile}:/root/.docker/config.json:ro".toString())
        }

        if(containerScanConfig.cacheDirectory){
            // cache directory
            wrapper.add('-v')
            wrapper.add("${containerScanConfig.cacheDirectory}:/root/.cache/:rw".toString())
        }
        return wrapper
    }
    private List<String> trivyWrapper(String containerScanner, String targetImage){
        List<String> wrapper = [containerScanner,
                                'image',
                                 targetImage]
        return wrapper
    }
}
