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
import io.seqera.wave.service.blob.BlobCacheInfo
import io.seqera.wave.service.blob.TransferStrategy
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildStrategy
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implement a service for job creation and execution
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class JobServiceImpl implements JobService {

    @Inject
    private JobOperation operations

    @Inject
    @Nullable
    private TransferStrategy transferStrategy

    @Inject
    private BuildStrategy buildStrategy

    @Inject
    private JobQueue jobQueue

    @Inject
    private JobFactory jobFactory

    @Override
    JobSpec launchTransfer(BlobCacheInfo blob, List<String> command) {
        if( !transferStrategy )
            throw new IllegalStateException("Blob cache service is not available - check configuration setting 'wave.blobCache.enabled'")
        // create the ID for the job transfer
        final job = jobFactory.transfer(blob.id())
        // submit the job execution
        transferStrategy.launchJob(job.schedulerId, command)
        // signal the transfer has been submitted
        jobQueue.offer(job)
        return job
    }

    @Override
    JobSpec launchBuild(BuildRequest request) {
        // create the unique job id for the build
        final job = jobFactory.build(request)
        // launch the build job
        buildStrategy.build(job.schedulerId, request)
        // signal the build has been submitted
        jobQueue.offer(job)
        return job
    }

    @Override
    JobState status(JobSpec jobId) {
        return operations.status(jobId)
    }

    @Override
    void cleanup(JobSpec jobId, Integer exitStatus) {
        operations.cleanup(jobId, exitStatus)
    }
}
