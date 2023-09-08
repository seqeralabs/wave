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

import io.kubernetes.client.PodLogs
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.apis.CoreV1Api

/**
 * Define an abstraction for K8s clients
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface K8sClient {

    ApiClient apiClient()

    default BatchV1Api batchV1Api() {
        new BatchV1Api(apiClient())
    }

    default CoreV1Api coreV1Api() {
        new CoreV1Api(apiClient())
    }

    default PodLogs podLogs() {
        new PodLogs(apiClient())
    }
}
