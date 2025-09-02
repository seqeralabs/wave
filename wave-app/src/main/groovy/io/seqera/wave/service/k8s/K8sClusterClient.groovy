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

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.util.ClientBuilder
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

/**
 * Implements K8s client that self-discover the config
 * from the running pod
 *
 * Read more: https://github.com/kubernetes-client/java/blob/master/examples/examples-release-13/src/main/java/io/kubernetes/client/examples/InClusterClientExample.java
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Requires(env = 'k8s', missingProperty = 'wave.build.k8s.configPath')
@Singleton
class K8sClusterClient implements K8sClient {

    @Override
    @Memoized
    ApiClient apiClient() {
        // loading the in-cluster config, including:
        //   1. service-account CA
        //   2. service-account bearer-token
        //   3. service-account namespace
        //   4. master endpoints(ip, port) from pre-set environment variables
        return ClientBuilder.cluster().build();
    }
}
