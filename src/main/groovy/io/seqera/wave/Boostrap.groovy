package io.seqera.wave

import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.util.StringUtils
import jakarta.inject.Inject

/**
 * Basic bean to log some info at boostrap
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Context
@CompileStatic
class Boostrap {

    @Inject RegistryCredentialsProvider provider

    @PostConstruct
    void init() {
        def dokCreds = provider.getCredentials('docker.io')
        def quayCreds = provider.getCredentials('quay.io')
        log.info "Docker.io registry credentials: username=${dokCreds?.username ?: '-'}; password=${StringUtils.redact(dokCreds?.password)}"
        log.info "Quay.io   registry credentials: username=${quayCreds?.username ?: '-'}; password=${StringUtils.redact(quayCreds?.password)}"
    }

}
