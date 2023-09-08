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
