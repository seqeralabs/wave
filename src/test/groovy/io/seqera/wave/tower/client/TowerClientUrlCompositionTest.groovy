package io.seqera.wave.tower.client

import spock.lang.Specification

class TowerClientUrlCompositionTest extends Specification{

    def 'compose user info endpoint'() {
        expect:
        TowerClient.userInfoEndpoint(endpoint).toString() == expected

        where:

        endpoint                 || expected
        'http://tower:8080'      || 'http://tower:8080/user-info'
        'http://tower:8080/'     || 'http://tower:8080/user-info'
        'http://tower:8080/api'  || 'http://tower:8080/api/user-info'
        'http://tower:8080/api/' || 'http://tower:8080/api/user-info'
        'https://tower.io'       || 'https://tower.io/user-info'
    }


    def 'fail compose userInfo with invalid towerEndpoint'() {
        when:
        TowerClient.userInfoEndpoint(endpoint)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == message

        where:

        endpoint     || message
        null         || 'towerEndpoint should not be null or empty'
        ""           || 'towerEndpoint should not be null or empty'
        "gar bage"   || 'invalid url'
        "tower.io"   || 'towerEndpoint should be a valid http or https url, got [tower.io]'
        "ftp://xyz"  || 'towerEndpoint should be a valid http or https url, got [ftp://xyz]'

    }


    def 'compose list credentials endpoint'() {
        expect:
        TowerClient.listCredentialsEndpoint(endpoint, workspaceId).toString() == expected

        where:

        endpoint                  | workspaceId || expected
        'http://tower:8080'       |   null      || 'http://tower:8080/credentials'
        'http://tower:8080'       |   100       || 'http://tower:8080/credentials?workspaceId=100'
        'http://tower:8080/'      |   null      || 'http://tower:8080/credentials'
        'http://tower:8080/'      |   100       || 'http://tower:8080/credentials?workspaceId=100'
        'http://tower:8080/api'   |   null      || 'http://tower:8080/api/credentials'
        'http://tower:8080/api'   |   100       || 'http://tower:8080/api/credentials?workspaceId=100'
        'http://tower:8080/api/'  |   null      || 'http://tower:8080/api/credentials'
        'http://tower:8080/api/'  |   0         || 'http://tower:8080/api/credentials?workspaceId=0'
    }

    def 'fail compose list credentials with invalid towerEndpoint'() {
        when:
        TowerClient.listCredentialsEndpoint(endpoint, workspaceId).toString()

        then:
        def e = thrown(IllegalArgumentException)
        e.message == message

        where:

        endpoint    | workspaceId || message
        null        | null        || "towerEndpoint should not be null or empty"
        null        | 1           || "towerEndpoint should not be null or empty"
        ""          | null        || "towerEndpoint should not be null or empty"
        ""          | 1           || "towerEndpoint should not be null or empty"
        "gar bage"  | null        || "invalid url"
        "gar bage"  | 1           || "invalid url"
        "tower.io"  | null        || "towerEndpoint should be a valid http or https url, got [tower.io]"
        "tower.io"  | 1           || "towerEndpoint should be a valid http or https url, got [tower.io]"
        "ftp://xyz" | null        || "towerEndpoint should be a valid http or https url, got [ftp://xyz]"
        "ftp://xyz" | 1           || "towerEndpoint should be a valid http or https url, got [ftp://xyz]"

    }

    def 'compose fetch credentials endpoint'() {
        expect:
        TowerClient.fetchCredentialsEndpoint(endpoint,credsId,keyId,workspaceId).toString() == expected

        where:

        endpoint                  | credsId | keyId | workspaceId || expected
        'http://tower:8080'       | '0'     | '0'   |  null      || 'http://tower:8080/credentials/0/keys?keyId=0'
        'http://tower:8080'       | '1'     | '1'   |  100       || 'http://tower:8080/credentials/1/keys?keyId=1&workspaceId=100'
        'http://tower:8080/'      | '1'     | '2'   |  null      || 'http://tower:8080/credentials/1/keys?keyId=2'
        'http://tower:8080/'      | '1'     | '3'   |  100       || 'http://tower:8080/credentials/1/keys?keyId=3&workspaceId=100'
        'http://tower:8080/api'   | '1'     | '4'   |  null      || 'http://tower:8080/api/credentials/1/keys?keyId=4'
        'http://tower:8080/api'   | '1'     | '5'   |  100       || 'http://tower:8080/api/credentials/1/keys?keyId=5&workspaceId=100'
        'http://tower:8080/api/'  | '1'     | '6'   |  null      || 'http://tower:8080/api/credentials/1/keys?keyId=6'
        'http://tower:8080/api/'  | '1'     | '7'   |  0         || 'http://tower:8080/api/credentials/1/keys?keyId=7&workspaceId=0'
    }

    def 'fail compose fetch credentials endpoint with invalid towerEndpoint or missing data'() {
        when:
        TowerClient.fetchCredentialsEndpoint(endpoint, credsId, keyId, 1)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == messsage

        where:

        endpoint     | credsId  | keyId | workspaceId  || messsage
        null         |  "10"    | "10"  |  1           ||  "towerEndpoint should not be null or empty"
        null         |  "10"    | "10"  |  null        ||  "towerEndpoint should not be null or empty"
        ""           |  "10"    | "10"  |  null        ||  "towerEndpoint should not be null or empty"
        ""           |  "10"    | "10"  |  null        ||  "towerEndpoint should not be null or empty"
        "gar bage"   |  "10"    | "10"  |  1           ||  "invalid url"
        "gar bage"   |  "10"    | "10"  |  null        ||  "invalid url"
        "tower.io"   |  "10"    | "10"  |  1           ||  "towerEndpoint should be a valid http or https url, got [tower.io]"
        "tower.io"   |  "10"    | "10"  |  null        ||  "towerEndpoint should be a valid http or https url, got [tower.io]"
        "ftp://xyz"  |  "10"    | "10"  |  1           ||  "towerEndpoint should be a valid http or https url, got [ftp://xyz]"
        "ftp://xyz"  |  "10"    | "10"  |  null        ||  "towerEndpoint should be a valid http or https url, got [ftp://xyz]"
        "http://xyz" |  null    | "10"  |  1           ||  "credentialsId should not be null or empty"
        "http://xyz" |  ""      | "10"  |  1           ||  "credentialsId should not be null or empty"
        "http://xyz" |  null    | "10"  |  null        ||  "credentialsId should not be null or empty"
        "http://xyz" |  ""      | "10"  |  null        ||  "credentialsId should not be null or empty"
        "http://xyz" |  "10"    | null  |  1           ||  "encryptionKey should not be null or empty"
        "http://xyz" |  "10"    | ""    |  1           ||  "encryptionKey should not be null or empty"
        "http://xyz" |  "10"    | null  |  null        ||  "encryptionKey should not be null or empty"
        "http://xyz" |  "10"    | ""    |  null        ||  "encryptionKey should not be null or empty"


    }

}
