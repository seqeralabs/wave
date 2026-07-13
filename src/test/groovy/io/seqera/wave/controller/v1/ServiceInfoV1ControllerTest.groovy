/*
 * Copyright 2026, Seqera Labs
 */
package io.seqera.wave.controller.v1

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.v1.model.ServiceInfoResponse
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class ServiceInfoV1ControllerTest extends Specification {

    @Inject @Client('/') HttpClient client

    def 'GET /w1/service-info returns version and commit id'() {
        when:
        def resp = client.toBlocking()
                .exchange(HttpRequest.GET('/w1/service-info'), ServiceInfoResponse)

        then:
        resp.status.code == 200
        resp.body().serviceInfo.version
        resp.body().serviceInfo.commitId
    }
}
