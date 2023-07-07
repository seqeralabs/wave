package io.seqera.wave.service.scan

import java.nio.file.Files
import java.nio.file.Path
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

        Instant startTime = Instant.now()
        Path configFile = null
        Path scanDir = null
        StringBuilder processOutput = new StringBuilder()
        try{
            if( buildRequest.configJson ) {
                scanDir = Files.createDirectories(Path.of(containerScanConfig.workspace))
                configFile = scanDir.resolve('config.json').toAbsolutePath()
                Files.write(configFile, JsonOutput.prettyPrint(buildRequest.configJson).bytes, CREATE, WRITE, TRUNCATE_EXISTING)
            }

        def dockerCommand = dockerWrapper(configFile)
        def trivyCommand = List.of(containerScanner)+trivyWrapper(buildRequest.targetImage)
        def command = dockerCommand+trivyCommand

        //launch scanning
        log.info("Container Scan Command "+command.join(' '))
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
            log.warn("Container scan failed to scan container, reason : ${e.getMessage()}")
            return ScanResult.failure(buildRequest.id, startTime, null)
        }finally {
            cleanup(scanDir)
        }
        return ScanResult.success(buildRequest.id, startTime, TrivyResultProcessor.process(processOutput.toString()))
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
    void cleanup(Path scanDir) {
        scanDir?.deleteDir()
    }
}
