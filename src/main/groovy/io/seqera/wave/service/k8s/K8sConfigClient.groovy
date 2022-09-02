package io.seqera.wave.service.k8s


import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.util.ClientBuilder
import io.kubernetes.client.util.KubeConfig
import io.micronaut.context.annotation.Requires
import io.seqera.wave.config.K8sConfiguration
import io.seqera.wave.config.WaveConfiguration
import jakarta.inject.Inject
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

    @Inject
    K8sConfiguration k8sConfiguration

    @Memoized
    ApiClient apiClient() {
        log.info "Creating K8s config with path: $k8sConfiguration.configPath -- context: '$k8sConfiguration.context'"

        // load config
        final config = KubeConfig.loadKubeConfig(new FileReader(k8sConfiguration.configPath))
        if( k8sConfiguration.context ) {
            config.setContext(k8sConfiguration.context)
        }

        // loading the out-of-cluster config, a kubeconfig from file-system
        return ClientBuilder
                .kubeconfig(config)
                .build()
    }

}
