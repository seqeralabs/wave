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

package io.seqera.wave.service.k8s

import java.nio.file.Path
import java.time.Duration

import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1Pod
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.configuration.MirrorConfig
import io.seqera.wave.configuration.ScanConfig
/**
 * Defines Kubernetes operations
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface K8sService {

    enum JobStatus { Pending, Running, Succeeded, Failed; boolean completed() { return this == Succeeded || this == Failed } }

    V1Pod getPod(String name)

    String logsPod(V1Pod pod)

    void deletePod(String name)

    JobStatus getJobStatus(String name)

    void deleteJob(String name)
  
    V1Job launchTransferJob(String name, String containerImage, List<String> args, BlobCacheConfig blobConfig)

    V1Job launchBuildJob(String name, String containerImage, List<String> args, Path workDir, Path creds, Duration timeout, Map<String,String> nodeSelector)

    V1Job launchScanJob(String name, String containerImage, List<String> args, Path workDir, Path creds, ScanConfig scanConfig)

    V1Job launchMirrorJob(String name, String containerImage, List<String> args, Path workDir, Path creds, MirrorConfig config)

    V1Pod getLatestPodForJob(String jobName)

}
