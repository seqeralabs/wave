package io.seqera.wave.service.blob.transfer

import java.util.concurrent.TimeUnit

import groovy.transform.CompileStatic
import io.seqera.wave.configuration.BlobConfig
import io.seqera.wave.service.blob.BlobInfo
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Simple {@link TransferStrategy} implementation that runs
 * s5cmd in the local computer. Meant for development purposes
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class LocalTransferStrategy implements TransferStrategy {

    @Inject
    private BlobConfig blobConfig

    @Override
    BlobInfo transfer(BlobInfo info, List<String> cli) {
        // launch the execution
        final proc = new ProcessBuilder()
                .command(cli)
                .redirectErrorStream(true)
                .start()
        // wait for the completion and save thr result
        final completed = proc.waitFor(blobConfig.transferTimeout.toSeconds(), TimeUnit.SECONDS)
        final int status = completed ? proc.exitValue() : -1
        final logs = proc.inputStream.text
        return info.completed(status, logs)
    }
}
