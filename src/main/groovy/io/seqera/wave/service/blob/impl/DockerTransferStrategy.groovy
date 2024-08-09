/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
        // create a unique name for the container
        final proc = createProcess(command,info.jobName).start()
        // wait for the completion and save the result
        final completed = proc.waitFor(blobConfig.transferTimeout.toSeconds(), TimeUnit.SECONDS)
        final int status = completed ? proc.exitValue() : -1
        final logs = proc.inputStream.text
        return info.completed(status, logs)
    }

    protected ProcessBuilder createProcess(List<String> command, String name) {
        // compose the docker command
        final cli = new ArrayList<String>(10)
        cli.add('docker')
        cli.add('run')
        cli.add('--name')
        cli.add(name)
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

    @Override
    Status status(BlobCacheInfo info) {
        final status = checkDockerContainerStatus(info.jobName)
        if(status == 'running') {
            return Status.RUNNING
        } else if(status == 'exited') {
            return Status.SUCCEED
        } else if (status == 'created' || status == 'paused') {
            return Status.PENDING
        } else {
            return Status.FAILED
        }
    }

    private static String checkDockerContainerStatus(String containerName) {
        def cli = new ArrayList<String>()
        cli.add('docker')
        cli.add('inspect')
        cli.add('--format')
        cli.add('{{.State.Status}}')
        cli.add(containerName)

        def builder = new ProcessBuilder(cli)
        builder.redirectErrorStream(true)
        def process = builder.start()
        def status = process.inputStream.text.trim()
        process.waitFor()
        return status
    }

}
