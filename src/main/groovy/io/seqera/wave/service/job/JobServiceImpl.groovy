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

import javax.annotation.Nullable

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.service.blob.BlobEntry
import io.seqera.wave.service.blob.TransferStrategy
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildStrategy
import io.seqera.wave.service.cleanup.CleanupService
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.mirror.strategy.MirrorStrategy
import io.seqera.wave.service.scan.ScanRequest
import io.seqera.wave.service.scan.ScanStrategy
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
    @Nullable
    private TransferStrategy transferStrategy

    @Inject
    private BuildStrategy buildStrategy

    @Inject
    @Nullable
    private ScanStrategy scanStrategy

    @Inject
    private JobQueue jobQueue

    @Inject
    private JobFactory jobFactory

    @Inject
    private MirrorStrategy mirrorStrategy

    @Override
    JobSpec launchTransfer(BlobEntry blob, List<String> command) {
        if( !transferStrategy )
            throw new IllegalStateException("Blob cache service is not available - check configuration setting 'wave.blobCache.enabled'")
        // create the ID for the job transfer
        final job = jobFactory.transfer(blob.getKey())
        // submit the job execution
        transferStrategy.launchJob(job.operationName, command)
        // signal the transfer has been submitted
        jobQueue.offer(job)
        return job
    }

    @Override
    JobSpec launchBuild(BuildRequest request) {
        // create the unique job id for the build
        final job = jobFactory.build(request)
        // launch the build job
        buildStrategy.build(job.operationName, request)
        // signal the build has been submitted
        jobQueue.offer(job)
        return job
    }

    @Override
    JobSpec launchScan(ScanRequest request) {
        if( !scanStrategy )
            throw new IllegalStateException("Container scan service is not available - check configuration setting 'wave.scan.enabled'")

        // create the unique job id for the build
        final job = jobFactory.scan(request)
        // launch the scan job
        scanStrategy.scanContainer(job.operationName, request)
        // signal the build has been submitted
        jobQueue.offer(job)
        return job
    }

    @Override
    JobSpec launchMirror(MirrorRequest request) {
        // create the unique job id for the build
        final job = jobFactory.mirror(request)
        // launch the scan job
        mirrorStrategy.mirrorJob(job.operationName, request)
        // signal the build has been submitted
        jobQueue.offer(job)
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
