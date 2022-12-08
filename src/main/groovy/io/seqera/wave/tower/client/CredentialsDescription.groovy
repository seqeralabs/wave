package io.seqera.wave.tower.client

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.CompileStatic
import io.seqera.wave.WaveDefault

@CompileStatic
class CredentialsDescription {

    String id
    String provider
    String registry


    @JsonProperty("keys")
    private void unpackRegistry(Map<String,Object> keys) {
        if (this.provider == 'container-reg') {
            this.registry = keys?.get("registry")?: WaveDefault.DOCKER_IO
        } else {
            this.registry = null
        }
    }
}
