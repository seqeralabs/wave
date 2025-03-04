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

package io.seqera.wave.service.job


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.service.blob.TransferRequest
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.cleanup.CleanupService
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.scan.ScanRequest
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implement a service for job creation and execution
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class JobServiceImpl implements JobService {

    @Inject
    private CleanupService cleanupService

    @Inject
    private JobOperation operations

    @Inject
    private JobPendingQueue jobQueue

    @Inject
    private JobFactory jobFactory

    @Override
    JobSpec launchTransfer(TransferRequest request) {
        // create the ID for the job transfer
        final job = jobFactory.transfer(request.key)
        // submit the job execution
        jobQueue.submit(new JobRequest(job, request))
        return job
    }

    @Override
    JobSpec launchBuild(BuildRequest request) {
        // create the unique job id for the build
        final job = jobFactory.build(request)
        // launch the build job
        jobQueue.submit(new JobRequest(job,request))
        return job
    }

    @Override
    JobSpec launchScan(ScanRequest request) {
        // create the unique job id for the build
        final job = jobFactory.scan(request)
        // launch the scan job
        jobQueue.submit(new JobRequest(job,request))
        return job
    }

    @Override
    JobSpec launchMirror(MirrorRequest request) {
        // create the unique job id for the build
        final job = jobFactory.mirror(request)
        // launch the scan job
        jobQueue.submit(new JobRequest(job,request))
        return job
    }

    @Override
    JobState status(JobSpec job) {
        try {
            return operations.status(job)
        }
        catch (Throwable t) {
            log.warn "Unable to obtain status for job=${job.operationName} - cause: ${t.message}", t
            return new JobState(JobState.Status.UNKNOWN, null, t.message)
        }
    }

    @Override
    void cleanup(JobSpec job, Integer exitStatus) {
        cleanupService.cleanupJob(job, exitStatus)
    }
}
