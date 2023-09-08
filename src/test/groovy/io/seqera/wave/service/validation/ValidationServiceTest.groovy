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
