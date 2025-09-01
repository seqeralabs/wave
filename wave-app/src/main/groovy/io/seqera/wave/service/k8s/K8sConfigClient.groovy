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

import io.micronaut.core.annotation.Nullable

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.util.ClientBuilder
import io.kubernetes.client.util.KubeConfig
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton

/**
 * Kubernetes client that connect to the cluster via kube
 * config in the local file system
 *
 * Check examples here
 * https://github.com/kubernetes-client/java/tree/master/examples/examples-release-13/src/main/java/io/kubernetes/client/examples
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Requires(property = 'wave.build.k8s.configPath')
@Singleton
class K8sConfigClient implements K8sClient {

    @Value('${wave.build.k8s.context}')
    @Nullable
    private String context

    @Value('${wave.build.k8s.configPath}')
    private String kubeConfigPath

    @Memoized
    ApiClient apiClient() {
        log.info "Creating K8s config with path: $kubeConfigPath -- context: '$context'"

        // load config
        final config = KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath))
        if( context ) {
            config.setContext(context)
        }

        // loading the out-of-cluster config, a kubeconfig from file-system
        return ClientBuilder
                .kubeconfig(config)
                .build()
    }

}
