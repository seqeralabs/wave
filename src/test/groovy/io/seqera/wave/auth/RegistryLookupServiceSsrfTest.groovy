/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2026, Seqera Labs
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

package io.seqera.wave.auth

import spock.lang.Specification
import spock.lang.Unroll

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.exception.BadRequestException
import jakarta.inject.Inject
/**
 * Verify the registry lookup refuses loopback, private, link-local and metadata
 * addresses before any connection is opened, when SSRF protection is enabled.
 *
 * @author Oussama El Azizi <oussama.elazizi@seqera.io>
 */
@MicronautTest
@Property(name = 'wave.security.ssrf-protection.enabled', value = 'true')
class RegistryLookupServiceSsrfTest extends Specification {

    @Inject
    RegistryLookupService lookupService

    @Unroll
    def 'should refuse lookup of #description'() {
        when:
        lookupService.lookup(registry)

        then:
        thrown(BadRequestException)

        where:
        registry              | description
        '127.0.0.1'           | 'loopback IP'
        '127.0.0.1:8080'      | 'loopback IP with port'
        'localhost'           | 'localhost'
        'localhost:5000'      | 'localhost with port'
        '0.0.0.0'             | 'wildcard address'
        '10.0.0.1:8080'       | 'RFC1918 class A address'
        '172.16.0.1'          | 'RFC1918 class B address'
        '192.168.1.1:5000'    | 'RFC1918 class C address'
        '169.254.169.254'     | 'cloud metadata address'
        '169.254.170.2'       | 'ECS metadata address'
    }

}
