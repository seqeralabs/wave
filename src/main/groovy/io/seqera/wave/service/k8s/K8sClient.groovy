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
