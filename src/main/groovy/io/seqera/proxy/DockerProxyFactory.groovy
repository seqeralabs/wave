package io.seqera.proxy

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Factory
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.http.client.netty.DefaultHttpClient
import io.micronaut.inject.qualifiers.Qualifiers
import io.seqera.config.RegistryBean

@Factory
class DockerProxyFactory {

    ApplicationContext applicationContext
    protected DockerProxyFactory(ApplicationContext applicationContext){
        this.applicationContext = applicationContext
    }

    @EachBean(RegistryBean)
    protected HttpClient httpClient(RegistryBean registryBean){
        HttpClientConfiguration configuration =
                applicationContext.containsBean(HttpClientConfiguration, Qualifiers.byName(registryBean.name)) ?
                        applicationContext.getBean(HttpClientConfiguration, Qualifiers.byName(registryBean.name)) :
                        new DefaultHttpClientConfiguration()
        new DefaultHttpClient( registryBean.host.toURI(),configuration)
    }

}
