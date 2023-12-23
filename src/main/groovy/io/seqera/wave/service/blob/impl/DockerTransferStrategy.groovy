package io.seqera.wave.service.blob.impl

import java.util.concurrent.TimeUnit

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.service.blob.BlobCacheInfo
import io.seqera.wave.service.blob.TransferStrategy
import jakarta.inject.Inject
/**
 * Implements {@link TransferStrategy} that runs s5cmd using a docker
 * container. Meant for development purposes
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Requires(property = 'wave.blobCache.strategy', value = 'docker')
@Replaces(SimpleTransferStrategy)
class DockerTransferStrategy implements TransferStrategy {

    @Inject
    private BlobCacheConfig blobConfig

    @Override
    BlobCacheInfo transfer(BlobCacheInfo info, List<String> command) {
        final proc = createProcess(command).start()
        // wait for the completion and save thr result
        final completed = proc.waitFor(blobConfig.transferTimeout.toSeconds(), TimeUnit.SECONDS)
        final int status = completed ? proc.exitValue() : -1
        final logs = proc.inputStream.text
        return info.completed(status, logs)
    }

    protected ProcessBuilder createProcess(List<String> command) {
        // compose the docker command
        final cli = new ArrayList<String>(10)
        cli.add('docker')
        cli.add('run')
        cli.add('-e')
        cli.add('AWS_ACCESS_KEY_ID')
        cli.add('-e')
        cli.add('AWS_SECRET_ACCESS_KEY')
        cli.add(blobConfig.s5Image)
        cli.addAll(command)
        log.debug "Transfer docker command: ${cli.join(' ')}\n"

        // proc builder
        final builder = new ProcessBuilder()
        builder.environment().putAll(blobConfig.getEnvironment())
        builder.command(cli)
        builder.redirectErrorStream(true)
        return builder
    }
}
