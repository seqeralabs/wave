/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

package io.seqera.wave.core

import spock.lang.Specification

import io.seqera.wave.auth.RegistryCredentials
import io.seqera.wave.auth.RegistryCredentialsProvider

/**
 * Verify the 'wave.inject-credentials' flag gates proxy-pull credential injection.
 *
 * @author Gavin Elder
 */
class RegistryProxyServiceCredentialsTest extends Specification {

    def 'should not inject credentials when disabled'() {
        given:
        def provider = Mock(RegistryCredentialsProvider)
        def service = new RegistryProxyService()
        service.@credentialsProvider = provider
        service.@injectCredentials = false
        and:
        def route = Mock(RoutePath)

        when:
        def result = service.getCredentials(route)

        then:
        0 * provider.getCredentials(_, _)
        and:
        result == null
    }

    def 'should inject credentials when enabled'() {
        given:
        def provider = Mock(RegistryCredentialsProvider)
        def creds = Mock(RegistryCredentials)
        def service = new RegistryProxyService()
        service.@credentialsProvider = provider
        service.@injectCredentials = true
        and:
        def route = Mock(RoutePath)

        when:
        def result = service.getCredentials(route)

        then:
        1 * provider.getCredentials(route, _) >> creds
        and:
        result == creds
    }

}
