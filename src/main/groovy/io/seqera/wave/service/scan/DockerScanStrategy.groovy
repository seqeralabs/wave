package io.seqera.wave.service.scan

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.model.ScanResult
import io.seqera.wave.service.builder.BuildRequest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import static java.nio.file.StandardOpenOption.CREATE
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import static java.nio.file.StandardOpenOption.WRITE

/**
 * Implements ScanStrategy for Docker
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@Requires(missingProperty = 'wave.build.k8s')
@CompileStatic
class DockerScanStrategy extends ScanStrategy {

    @Inject
    private ScanConfig containerScanConfig

    DockerScanStrategy(ScanConfig containerScanConfig) {
        this.containerScanConfig = containerScanConfig
    }

    @Override
    ScanResult scanContainer(String scannerImage, BuildRequest req) {
        log.info("Launching container scan for buildId: ${req.id}")
        final startTime = Instant.now()

        try {
            // create the scan dir
            try {
                Files.createDirectory(req.scanDir)
            }
            catch (FileAlreadyExistsException e) {
                log.warn("Container scan directory already exists: $e")
            }

            // save the config file with docker auth credentials
            Path configFile = null
            if( req.configJson ) {
                configFile = req.scanDir.resolve('config.json')
                Files.write(configFile, JsonOutput.prettyPrint(req.configJson).bytes, CREATE, WRITE, TRUNCATE_EXISTING)
            }

            // outfile file name
            final reportFile = req.scanDir.resolve(Trivy.OUTPUT_FILE_NAME)
            // create the launch command 
            final dockerCommand = dockerWrapper(req.scanDir, configFile)
            final trivyCommand = List.of(scannerImage) + scanCommand(req.targetImage, reportFile)
            final command = dockerCommand + trivyCommand

            //launch scanning
            log.debug("Container scan command: ${command.join(' ')}")
            final process = new ProcessBuilder()
                    .command(command)
                    .redirectErrorStream(true)
                    .start()

            final exitCode = process.waitFor()
            if ( exitCode != 0 ) {
                log.warn("Container scan failed to scan container, it exited with code: ${exitCode} - cause: ${process.text}")
                return ScanResult.failure(req.id, startTime, null)
            }
            else{
                log.info("Container scan completed for buildId: ${req.id}")
                return ScanResult.success(req.id, startTime, TrivyResultProcessor.process(reportFile.text))
            }
        }
        catch (Exception e){
            log.warn("Container scan failed to scan container, reason: ${e.getMessage()}")
            return ScanResult.failure(req.id, startTime, null)
        }
    }

    protected List<String> dockerWrapper(Path scanDir, Path credsFile) {

        final wrapper = ['docker','run', '--rm']
        
        // scan work dir
        wrapper.add('-w')
        wrapper.add(scanDir.toString())

        wrapper.add('-v')
        wrapper.add("$scanDir:$scanDir:rw".toString())

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
