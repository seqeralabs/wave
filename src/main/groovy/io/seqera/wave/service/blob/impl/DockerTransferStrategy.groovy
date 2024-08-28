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

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.service.job.JobId
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.job.JobStrategy
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements {@link JobStrategy} that runs s5cmd using a docker
 * container. Meant for development purposes
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Singleton
@Requires(missingProperty = 'wave.build.k8s')
@Requires(property = 'wave.blobCache.enabled', value = 'true')
class DockerTransferStrategy implements JobStrategy {

    @Inject
    private BlobCacheConfig blobConfig

    @Override
    void launchJob(String jobName, List<String> command) {
        // create a unique name for the container
        createProcess(command, jobName, blobConfig.transferTimeout.toSeconds())
                .start()
    }

    protected ProcessBuilder createProcess(List<String> command, String name, long timeoutSecs) {
        // compose the docker command
        final cli = new ArrayList<String>(10)
        cli.add('docker')
        cli.add('run')
        cli.add('--name')
        cli.add(name)
        cli.add('--stop-timeout')
        cli.add(String.valueOf(timeoutSecs))
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
    JobState status(JobId job) {
        final state = getDockerContainerState(job.schedulerId)
        log.trace "Docker transfer status name=$job.schedulerId; state=$state"
        
        if (state.status == 'running') {
            return JobState.running()
        }
        else if (state.status == 'exited') {
            final logs = getDockerContainerLogs(job.schedulerId)
            return JobState.completed(state.exitCode, logs)
        }
        else if (state.status == 'created' || state.status == 'paused') {
            return JobState.pending()
        }
        else {
            final logs = getDockerContainerLogs(job.schedulerId)
            return JobState.unknown(logs)
        }
    }

    @Override
    void cleanup(JobId jobId, Integer exitStatus) {
        final cli = new ArrayList<String>()
        cli.add('docker')
        cli.add('rm')
        cli.add(jobId.schedulerId)

        final builder = new ProcessBuilder(cli)
        builder.redirectErrorStream(true)
        final process = builder.start()
        process.waitFor()
    }

    @ToString(includePackage = false, includeNames = true)
    @Canonical
    static class State {
        String status
        Integer exitCode

        static State parse(String result) {
            final ret = result.tokenize(',')
            final status = ret[0]
            final exit = ret[1] ? Integer.valueOf(ret[1]) : null
            new State(status,exit)
        }
    }

    private static State getDockerContainerState(String containerName) {
        final cli = new ArrayList<String>()
        cli.add('docker')
        cli.add('inspect')
        cli.add('--format')
        cli.add('{{.State.Status}},{{.State.ExitCode}}')
        cli.add(containerName)

        final builder = new ProcessBuilder(cli)
        builder.redirectErrorStream(true)
        final process = builder.start()
        process.waitFor()
        final result = process.inputStream.text.trim()
        return State.parse(result)
    }

    private static String getDockerContainerLogs(String containerName) {
        final cli = new ArrayList<String>()
        cli.add('docker')
        cli.add('logs')
        cli.add(containerName)

        final builder = new ProcessBuilder(cli)
        builder.redirectErrorStream(true)
        final process = builder.start()
        process.waitFor()
        process.inputStream.text
    }

}
