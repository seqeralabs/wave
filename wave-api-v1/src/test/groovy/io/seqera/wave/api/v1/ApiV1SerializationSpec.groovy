/*
 * Copyright 2026, Seqera Labs
 */
package io.seqera.wave.api.v1

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.seqera.wave.api.v1.model.ContainerRequest
import io.seqera.wave.api.v1.model.ServiceInfo
import io.seqera.wave.api.v1.model.ServiceInfoResponse
import spock.lang.Specification

class ApiV1SerializationSpec extends Specification {

    def mapper = new ObjectMapper().registerModule(new JavaTimeModule())

    def 'ContainerRequest round-trips through JSON without losing core fields'() {
        given:
        def req = new ContainerRequest()
        req.setContainerPlatform('linux/amd64')
        req.setContainerImage('docker.io/library/ubuntu:latest')

        when:
        def json = mapper.writeValueAsString(req)
        def back = mapper.readValue(json, ContainerRequest)

        then:
        back.containerPlatform == 'linux/amd64'
        back.containerImage == 'docker.io/library/ubuntu:latest'
    }

    def 'ServiceInfoResponse round-trips through JSON'() {
        given:
        def info = new ServiceInfo()
        info.setVersion('1.0.0')
        info.setCommitId('abcd1234')
        def resp = new ServiceInfoResponse()
        resp.setServiceInfo(info)

        when:
        def json = mapper.writeValueAsString(resp)
        def back = mapper.readValue(json, ServiceInfoResponse)

        then:
        back.serviceInfo.version == '1.0.0'
        back.serviceInfo.commitId == 'abcd1234'
    }
}
