/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.service.k8s

import java.nio.file.Path

import io.kubernetes.client.openapi.models.V1ContainerStateTerminated
import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1Pod
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

    V1ContainerStateTerminated waitPod(V1Pod pod, long timeout)
}
