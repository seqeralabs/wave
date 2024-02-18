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

import io.kubernetes.client.openapi.models.V1ContainerStateTerminated
import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1Pod
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.configuration.PackagesConfig
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.configuration.SpackConfig
/**
 * Defines Kubernetes operations
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface K8sService {

    enum JobStatus { Pending, Running, Succeeded, Failed }

    V1Job createJob(String name, String containerImage, List<String> args)

    V1Job getJob(String name)

    JobStatus getJobStatus(String name)

    V1Pod getPod(String name)

    String logsPod(String name)

    void deletePod(String name)

    V1Pod buildContainer(String name, String containerImage, List<String> args, Path workDir, Path creds, SpackConfig spackConfig, Map<String,String> nodeSelector)

    V1Pod scanContainer(String name, String containerImage, List<String> args, Path workDir, Path creds, ScanConfig scanConfig, Map <String,String> nodeSelector)

    V1Pod transferContainer(String name, String containerImage, List<String> args, BlobCacheConfig blobConfig)

    V1ContainerStateTerminated waitPod(V1Pod pod, long timeout)

    V1Pod packagesFetcherContainer(String name, String containerImage, List<String> args, Path workDir, PackagesConfig config)
}
