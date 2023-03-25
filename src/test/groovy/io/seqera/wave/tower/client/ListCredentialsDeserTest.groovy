package io.seqera.wave.tower.client


import spock.lang.Specification

import io.seqera.wave.WaveDefault
import io.seqera.wave.util.JacksonHelper

class ListCredentialsDeserTest extends Specification{


    def "should deserialize credentials list"() {
        given: 'a response payload from tower'
        def json = """
            {"credentials":[
                {"id":"1J4sAfA0RfKTcDPc5NOe9u",
                "name":"credentials",
                "description":null,
                "provider":"local",
                "baseUrl":null,
                "category":null,
                "deleted":null,
                "lastUsed":null,
                "dateCreated":"2022-11-24T10:53:41.962852+01:00",
                "lastUpdated":"2022-11-24T10:53:41.962852+01:00",
                "keys":{"password":null,"discriminator":"local"}},
               {"id":"1S7A9csbEQOsudkVFRLckq",
                "name":"credsTest",
                "description":null,
                "provider":"container-reg",
                "baseUrl":null,
                "category":null,
                "deleted":null,
                "lastUsed":null,
                "dateCreated":"2022-12-07T12:28:31.485811+01:00",
                "lastUpdated":"2022-12-07T12:28:31.485811+01:00",
                "keys":{"userName":"andrea","password":null,"registry":"quay.io","discriminator":"container-reg"}},
               {"id":"1X3Yzi146GZ1Hlkix7IXZB",
                "name":"Another",
                "description":null,
                "provider":"container-reg",
                "baseUrl":null,
                "category":null,
                "deleted":null,
                "lastUsed":null,
                "dateCreated":"2022-12-07T12:55:20.957536+01:00",
                "lastUpdated":"2022-12-07T12:55:20.957536+01:00",
                "keys":{"userName":"whatever","password":null,"registry":null,"discriminator":"container-reg"}}]}"""

        when: 'deserializing the object'
        def resp = JacksonHelper.fromJson(json, ListCredentialsResponse)
        then:
        resp.credentials?.size() == 3
        resp.credentials[0].registry == null
        resp.credentials[1].registry == 'quay.io'
        resp.credentials[2].registry == WaveDefault.DOCKER_IO

    }
}
