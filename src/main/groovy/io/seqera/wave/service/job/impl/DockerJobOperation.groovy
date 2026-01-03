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

package io.seqera.wave.service.job.impl

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.docker.cli.DockerCli
import io.seqera.wave.service.job.JobOperation
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import jakarta.inject.Singleton

/**
 * Docker implementation for {@link io.seqera.wave.service.job.JobService}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Singleton
@Requires(missingProperty = 'wave.build.k8s')
class DockerJobOperation implements JobOperation {

    private final DockerCli docker = new DockerCli()

    @Override
    JobState status(JobSpec jobSpec) {
        final state = docker.inspect(jobSpec.operationName)
        log.trace "Docker container status name=${jobSpec.operationName}; state=${state}"

        if (state.isRunning()) {
            return JobState.running()
        }
        else if (state.isExited()) {
            final logs = docker.logs(jobSpec.operationName)
            return JobState.completed(state.exitCode(), logs)
        }
        else if (state.isPending()) {
            return JobState.pending()
        }
        else {
            log.warn "Unexpected state for container state=${state}"
            final logs = docker.logs(jobSpec.operationName)
            return JobState.unknown(logs)
        }
    }

    @Override
    void cleanup(JobSpec jobSpec) {
        cleanup(jobSpec.operationName)
    }

    @Override
    void cleanup(String operationName) {
        docker.rm(operationName, false)
    }

}
