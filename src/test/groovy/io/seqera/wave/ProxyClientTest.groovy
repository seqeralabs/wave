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

import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.auth.RegistryAuth
import io.seqera.wave.auth.RegistryAuthService
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.proxy.ProxyClient
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ProxyClientTest extends Specification {

    @Inject
    @Shared
    ApplicationContext applicationContext

    @Inject RegistryLookupService lookupService
    @Inject RegistryAuthService loginService
    @Inject RegistryCredentialsProvider credentialsProvider

    @Inject HttpClientConfig httpConfig

    @Inject
    @Client("proxy-client")
    HttpClient httpClient

    def 'should call target manifests on docker.io' () {
        given:
        def REG = 'docker.io'
        def IMAGE = 'library/hello-world'
        def registry = lookupService.lookup(REG)
        def creds = credentialsProvider.getDefaultCredentials(REG)
        and:
        def proxy = new ProxyClient(httpClient, httpConfig)
                .withImage(IMAGE)
                .withRegistry(registry)
                .withLoginService(loginService)
                .withCredentials(creds)

        when:
        def resp1 = proxy.getString('/v2/library/hello-world/manifests/sha256:aa0cc8055b82dc2509bed2e19b275c8f463506616377219d9642221ab53cf9fe')
        and:
        then:
        resp1.code() == 200
    }

    def 'should call target manifests on docker.io with no creds' () {
        given:
        def REG = 'docker.io'
        def IMAGE = 'library/hello-world'
        def registry = lookupService.lookup(REG)
        and:
        def proxy = new ProxyClient(httpClient, httpConfig)
                .withImage(IMAGE)
                .withRegistry(registry)
                .withLoginService(loginService)

        when:
        def resp1 = proxy.getString('/v2/library/hello-world/manifests/sha256:aa0cc8055b82dc2509bed2e19b275c8f463506616377219d9642221ab53cf9fe')
        and:
        then:
        resp1.code() == 200
    }

    def 'should call target blob on quay' () {
        given:
        def REG = 'quay.io'
        def IMAGE = 'biocontainers/fastqc'
        def registry = lookupService.lookup(REG)
        def creds = credentialsProvider.getDefaultCredentials(REG)
        and:
        def proxy = new ProxyClient(httpClient, httpConfig)
                .withImage(IMAGE)
                .withRegistry(registry)
                .withLoginService(loginService)
                .withCredentials(creds)

        when:
        def resp1 = proxy.getString('/v2/biocontainers/fastqc/blobs/sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4')
        and:
        then:
        resp1.code() == 200
    }

    def 'should redirect a target blob on quay' () {
        given:
        def REG = 'quay.io'
        def IMAGE = 'biocontainers/fastqc'
        def registry = lookupService.lookup(REG)
        def creds = credentialsProvider.getDefaultCredentials(REG)
        and:
        def proxy = new ProxyClient(httpClient, httpConfig)
                .withImage(IMAGE)
                .withRegistry(registry)
                .withLoginService(loginService)
                .withCredentials(creds)

        when:
        def resp1 = proxy.getString('/v2/biocontainers/fastqc/blobs/sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4',null,false)
        and:
        then:
        resp1.code() == 302
    }

    def 'should lookup aws registry' () {
        when:
        def registry = lookupService.lookup('195996028523.dkr.ecr.eu-west-1.amazonaws.com')
        then:
        registry.name == '195996028523.dkr.ecr.eu-west-1.amazonaws.com'
        registry.host == new URI('https://195996028523.dkr.ecr.eu-west-1.amazonaws.com')
        registry.auth.realm == new URI('https://195996028523.dkr.ecr.eu-west-1.amazonaws.com/')
        registry.auth.service == 'ecr.amazonaws.com'
        registry.auth.type == RegistryAuth.Type.Basic
        registry.auth.endpoint == new URI('https://195996028523.dkr.ecr.eu-west-1.amazonaws.com/v2/')
    }

    @Requires({System.getenv('AWS_ACCESS_KEY_ID') && System.getenv('AWS_SECRET_ACCESS_KEY')})
    def 'should call target manifest on amazon' () {
        given:
        def IMAGE = 'wave/kaniko'
        def REG = '195996028523.dkr.ecr.eu-west-1.amazonaws.com'
        def registry = lookupService.lookup(REG)
        def creds = credentialsProvider.getDefaultCredentials(REG)
        and:
        def proxy = new ProxyClient(httpClient, httpConfig)
                .withImage(IMAGE)
                .withRegistry(registry)
                .withLoginService(loginService)
                .withCredentials(creds)

        when:
        def resp = proxy.getString("/v2/$IMAGE/manifests/0.1.0")
        then:
        resp.code() == 200
    }

    @Requires({System.getenv('AWS_ACCESS_KEY_ID') && System.getenv('AWS_SECRET_ACCESS_KEY')})
    def 'should call target manifest on ecr public' () {
        given:
        def IMAGE = 'seqera-labs/nextflow'
        def REG = 'public.ecr.aws'
        def registry = lookupService.lookup(REG)
        def creds = credentialsProvider.getDefaultCredentials(REG)
        and:
        def proxy = new ProxyClient(httpClient, httpConfig)
                .withImage(IMAGE)
                .withRegistry(registry)
                .withLoginService(loginService)
                .withCredentials(creds)

        when:
        def resp = proxy.getString("/v2/$IMAGE/manifests/23.04.0")
        then:
        resp.code() == 200
    }

    @Requires({System.getenv('GOOGLECR_KEYS')})
    void 'should lookup google artifactory' () {
        when:
        def registry = lookupService.lookup('europe-southwest1-docker.pkg.dev')
        then:
        registry.name == 'europe-southwest1-docker.pkg.dev'
        registry.host == new URI('https://europe-southwest1-docker.pkg.dev')
        registry.auth.realm == new URI('https://europe-southwest1-docker.pkg.dev/v2/token')
        !registry.auth.service
        registry.auth.type == RegistryAuth.Type.Bearer
        registry.auth.endpoint == new URI('https://europe-southwest1-docker.pkg.dev/v2/token')
    }

    /*
    This test requires a key for an account service created in the project wave-test-361419
    https://console.cloud.google.com/welcome?project=wave-test-361419

    This account needs to have following roles:
    - Cloud Storage read/write
    - Artifact read/write
     */
    @Requires({System.getenv('GOOGLECR_KEYS')})
    def 'should login on google' () {
        given:
        def KEY = System.getenv('GOOGLECR_KEYS')
        and:
        def registry = 'europe-southwest1-docker.pkg.dev'
        def USER = '_json_key' // or '_json_key_base64'

        expect:
        loginService.login(registry, USER, KEY)
    }

    /*
    This test requires a key for an account service created in the project wave-test-361419
    https://console.cloud.google.com/welcome?project=wave-test-361419

    This account needs to have following roles:
    - Cloud Storage read/write
    - Artifact read/write
     */
    @Requires({System.getenv('GOOGLECR_KEYS')})
    def 'should call target manifest on google' () {
        given:
        def IMAGE = 'wave-test-361419/wave-build/hello-world'
        def REG = 'europe-southwest1-docker.pkg.dev'
        def registry = lookupService.lookup(REG)
        def creds = credentialsProvider.getDefaultCredentials(REG)
        and:
        def proxy = new ProxyClient(httpClient, httpConfig)
                .withImage(IMAGE)
                .withRegistry(registry)
                .withLoginService(loginService)
                .withCredentials(creds)

        when:
        def resp = proxy.getString("/v2/$IMAGE/manifests/latest")
        then:
        resp.code() == 200
    }
}
