/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave

import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.seqera.wave.proxy.LoginResponse
import spock.lang.IgnoreIf
import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class MicronautClientTest extends Specification{

    @IgnoreIf({ System.getenv("DOCKER_USER") == null})
    def 'should call target blob' () {
        given:
        def username = System.getenv("DOCKER_USER")
        def IMAGE = 'library/hello-world'
        def DIGEST = 'sha256:feb5d9fea6a5e9606aa995e879d862b825965ba48de054caab5ef356dc6b3412'
        def pat = System.getenv("DOCKER_PAT")
        def client = HttpClient.create(new URL('https://auth.docker.io'))
        def login = "/token?service=registry.docker.io&scope=repository:${IMAGE}:pull"
        def basic = "$username:$pat".bytes.encodeBase64()
        def auth = "Basic $basic"
        
        when:
        HttpRequest request = HttpRequest.create(HttpMethod.GET, login)
                .header("Authorization", auth.toString())
        HttpResponse<LoginResponse> resp = client.toBlocking().exchange(request, LoginResponse);
        println resp.body()
        then:
        resp.status() == HttpStatus.OK
        resp.body().access_token

        when:
        def token = resp.body().token
        def registry = HttpClient.create(new URL(' https://registry-1.docker.io'))
        def req1 = HttpRequest.create(HttpMethod.GET, "/v2/library/hello-world/blobs/sha256:feb5d9fea6a5e9606aa995e879d862b825965ba48de054caab5ef356dc6b3412")
                    .header("Authorization", "Bearer ${token}")
                    .header('Accept-Encoding', 'identity')
//                    .accept("application/json")
//                    .accept("application/vnd.docker.distribution.manifest.v2+json")
//                    .accept("application/vnd.docker.distribution.manifest.list.v2+json")
//                    .accept("application/vnd.oci.image.index.v1+json")
//                    .accept("application/vnd.docker.distribution.manifest.v1+prettyjws")
//                    .accept("application/vnd.oci.image.manifest.v1+json")
        def resp1 = registry.toBlocking().exchange(req1);
        then:
        resp1.status == HttpStatus.OK
    }
}
