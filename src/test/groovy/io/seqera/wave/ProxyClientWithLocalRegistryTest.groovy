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

package io.seqera.wave

import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient

import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.auth.RegistryAuthService
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.proxy.ProxyClient
import io.seqera.wave.test.DockerRegistryContainer
import jakarta.inject.Inject
import jakarta.inject.Named

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ProxyClientWithLocalRegistryTest extends Specification implements DockerRegistryContainer{

    @Inject
    @Shared
    ApplicationContext applicationContext

    @Inject RegistryLookupService lookupService
    @Inject RegistryAuthService loginService
    @Inject RegistryCredentialsProvider credentialsProvider
    @Inject @Named("never-redirects") HttpClient httpClient

    def setupSpec() {
        initRegistryContainer(applicationContext)
    }

    def 'should call target blob' () {
        given:
        def IMAGE = 'library/hello-world'
        and:
        def proxy = new ProxyClient(httpClient)
                .withImage(IMAGE)
                .withRegistry(getLocalTestRegistryInfo())
                .withLoginService(loginService)

        when:
        def resp1 = proxy.getString('/v2/library/hello-world/manifests/latest')
        and:
        println resp1.body()
        then:
        resp1.statusCode() == 200
    }

}
