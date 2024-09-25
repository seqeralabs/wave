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

import io.seqera.wave.service.blob.BlobState
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.scan.ScanRequest

/**
 * Define the contract for submitting and monitoring jobs
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface JobService {

    JobSpec launchTransfer(BlobState blob, List<String> command)

    JobSpec launchBuild(BuildRequest request)

    JobSpec launchScan(ScanRequest request)

    JobSpec launchMirror(MirrorRequest request)

    JobState status(JobSpec jobSpec)

    void cleanup(JobSpec jobSpec, Integer exitStatus)

}
