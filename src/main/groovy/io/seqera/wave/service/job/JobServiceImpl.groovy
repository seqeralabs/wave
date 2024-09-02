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
import io.seqera.wave.service.blob.BlobCacheInfo
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
    private JobStrategy jobStrategy

    @Inject
    private JobQueue jobQueue

    @Override
    JobId launchTransfer(BlobCacheInfo blob, List<String> command) {
        // create the ID for the job transfer
        final job = JobId.transfer(blob.id())
        // submit the job execution
        jobStrategy.launchJob(job.schedulerId, command)
        // signal the transfer to be started
        jobQueue.offer(job)
        return job
    }

}
