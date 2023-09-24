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

package io.seqera.wave.service.validation

import spock.lang.Specification
import spock.lang.Unroll

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ValidationServiceTest extends Specification {

    @Inject
    ValidationService validationService

    @Unroll
    def 'should check valid endpoint' () {
        expect:
        validationService.checkEndpoint(ENDPOINT)==EXPECTED

        where:
        ENDPOINT                | EXPECTED
        'foo'                   | "Missing endpoint protocol — offending value: foo"
        'ftp://foo.com'         | "Invalid endpoint protocol — offending value: ftp://foo.com"
        'http://a b c'          | "Invalid endpoint 'http://a b c' — cause: Illegal character in authority at index 7: http://a b c"
        and:
        'http://foo.com'        | null
        'http://localhost'      | null
        'http://localhost:8000' | null
        'https://foo.com/api'   | null
        'https://a.b.c/api'     | null
        'https://a.b.c:80/api'  | null
    }


    @Unroll
    def 'should check valid container na,e' () {
        expect:
        validationService.checkContainerName(CONTAINER)==EXPECTED

        where:
        CONTAINER                   | EXPECTED
        'foo'                       | null
        'foo:latest'                | null
        'library/foo:latest'        | null
        'quay.io/foo:latest'        | null
        'quay.io:80/foo:latest'     | null
        'localhost:8000/foo:latest' | null
        and:
        'docker:quay.io/foo:latest'  | 'Invalid container image name — offending value: docker:quay.io/foo:latest'
        'http://quay.io/foo:latest'  | 'Invalid container repository name — offending value: http://quay.io/foo:latest'
        'http://quay.io/foo:latest'  | 'Invalid container repository name — offending value: http://quay.io/foo:latest'
    }
}
