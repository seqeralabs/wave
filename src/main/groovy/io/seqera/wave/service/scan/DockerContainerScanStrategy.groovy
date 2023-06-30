package io.seqera.wave.service.scan


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.builder.BuildRequest
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
    @Override
    String scanContainer(String containerScanner, BuildRequest buildRequest) {
        String image = buildRequest.targetImage
        log.info("Launching container scan for buildId: "+buildRequest.id)
        def dockerCommand = dockerWrapper()
        def trivyCommand = trivyWrapper(containerScanner, image)
        def command = dockerCommand+trivyCommand
        //launch scanning
        log.info("Conatienr Scan Command "+command.join(' '))
        Process process = new ProcessBuilder()
                .command(command)
                .start()

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
                InputStream errorStream = process.getErrorStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    log.warn(line);
                }
            } else{
                log.info("Container scan completed for buildId: "+buildRequest.id)
            }
        }catch (Exception e){
            log.warn("Container scanner failed to scan container, reason : ${e.getMessage()}")
        }

        return processOutput.toString()
    }

    private List<String> dockerWrapper() {
        List<String> wrapper = ['docker',
                                'run',
                                '--rm',
                                '-e',
                                'AWS_ACCESS_KEY_ID='+System.getenv('AWS_ACCESS_KEY_ID'),
                                '-e',
                                'AWS_SECRET_ACCESS_KEY='+System.getenv('AWS_SECRET_ACCESS_KEY'),
                                '-e',
                                'AWS_DEFAULT_REGION=eu-west-1',
                                ]
        return wrapper
    }
    private List<String> trivyWrapper(String containerScanner, String image){
        List<String> wrapper = [containerScanner,
                                '--format',
                                'json',
                                'image',
                                 image]
    }
}
