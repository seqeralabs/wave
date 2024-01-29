package io.seqera.wave.service.blob.impl

import java.util.concurrent.TimeUnit

import groovy.transform.CompileStatic
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.service.blob.BlobCacheInfo
import io.seqera.wave.service.blob.TransferStrategy
import jakarta.inject.Inject
/**
 * Simple {@link TransferStrategy} implementation that runs
 * s5cmd in the local computer. Meant for development purposes
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class SimpleTransferStrategy implements TransferStrategy {

    @Inject
    private BlobCacheConfig blobConfig

    @Override
    BlobCacheInfo transfer(BlobCacheInfo info, List<String> cli) {
        final proc = createProcess(cli).start()
        // wait for the completion and save thr result
        final completed = proc.waitFor(blobConfig.transferTimeout.toSeconds(), TimeUnit.SECONDS)
        final int status = completed ? proc.exitValue() : -1
        final logs = proc.inputStream.text
        return info.completed(status, logs)
    }

    protected ProcessBuilder createProcess(List<String> cli) {
        // builder
        final builder = new ProcessBuilder()
        builder.environment().putAll(blobConfig.getEnvironment())
        builder.command(cli)
        builder.redirectErrorStream(true)
        return builder
    }
}
