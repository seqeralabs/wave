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
        thrown(IllegalArgumentException)

        where:
        endpoint     || _
        null         || _
        ""           || _
        "gar bage"   || _
        "tower.io"   || _
        "ftp://xyz"  || _

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
        thrown(IllegalArgumentException)

        where:

        endpoint    | workspaceId
        null        | null
        null        | 1
        ""          | null
        ""          | 1
        "gar bage"  | null
        "gar bage"  | 1
        "tower.io"  | null
        "tower.io"  | 1
        "ftp://xyz" | null
        "ftp://xyz" | 1          

    }

    def 'compose fetch credentials endpoint'() {
        expect:
        TowerClient.fetchCredentialsEndpoint(endpoint,credsId,keyId,workspaceId).toString() == expected

        where:

        endpoint                  | credsId | keyId | workspaceId || expected
        'http://tower:8080'       | '0'     | '0'   |  null      || 'http://tower:8080/credentials/0/keys?pairingId=0&keyId=0'
        'http://tower:8080'       | '1'     | '1'   |  100       || 'http://tower:8080/credentials/1/keys?pairingId=1&keyId=1&workspaceId=100'
        'http://tower:8080/'      | '1'     | '2'   |  null      || 'http://tower:8080/credentials/1/keys?pairingId=2&keyId=2'
        'http://tower:8080/'      | '1'     | '3'   |  100       || 'http://tower:8080/credentials/1/keys?pairingId=3&keyId=3&workspaceId=100'
        'http://tower:8080/api'   | '1'     | '4'   |  null      || 'http://tower:8080/api/credentials/1/keys?pairingId=4&keyId=4'
        'http://tower:8080/api'   | '1'     | '5'   |  100       || 'http://tower:8080/api/credentials/1/keys?pairingId=5&keyId=5&workspaceId=100'
        'http://tower:8080/api/'  | '1'     | '6'   |  null      || 'http://tower:8080/api/credentials/1/keys?pairingId=6&keyId=6'
        'http://tower:8080/api/'  | '1'     | '7'   |  0         || 'http://tower:8080/api/credentials/1/keys?pairingId=7&keyId=7&workspaceId=0'
    }

    def 'fail compose fetch credentials endpoint with invalid towerEndpoint or missing data'() {
        when:
        TowerClient.fetchCredentialsEndpoint(endpoint, credsId, keyId, 1)

        then:
        thrown(IllegalArgumentException)

        where:

        endpoint     | credsId  | keyId | workspaceId
        null         |  "10"    | "10"  |  1
        null         |  "10"    | "10"  |  null
        ""           |  "10"    | "10"  |  null
        ""           |  "10"    | "10"  |  null
        "gar bage"   |  "10"    | "10"  |  1
        "gar bage"   |  "10"    | "10"  |  null
        "tower.io"   |  "10"    | "10"  |  1
        "tower.io"   |  "10"    | "10"  |  null
        "ftp://xyz"  |  "10"    | "10"  |  1
        "ftp://xyz"  |  "10"    | "10"  |  null
        "http://xyz" |  null    | "10"  |  1
        "http://xyz" |  ""      | "10"  |  1
        "http://xyz" |  null    | "10"  |  null        
        "http://xyz" |  ""      | "10"  |  null
        "http://xyz" |  "10"    | null  |  1
        "http://xyz" |  "10"    | ""    |  1
        "http://xyz" |  "10"    | null  |  null
        "http://xyz" |  "10"    | ""    |  null


    }

}
