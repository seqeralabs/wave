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

package io.seqera.wave.exchange

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RegistryErrorResponseTest extends Specification {

    def 'should create an error response' () {
        when:
        def resp = new RegistryErrorResponse('FOO', 'Oops something went wrong')
        then:
        resp.getErrors().size()
        resp.getErrors().first() == new RegistryErrorResponse.RegistryError('FOO', 'Oops something went wrong')
    }

    def 'should create from json' () {
        given:
        def json = '{"errors":[{"code":"DENIED","message":"Unauthenticated request. Unauthenticated requests do not have permission \"artifactregistry.repositories.downloadArtifacts\" on resource \"projects/wired-height-305919/locations/us-central1/repositories/fsdx-docker-dev\" (or it may not exist)"}]}'
        when:
        def resp = RegistryErrorResponse.parse(json)
        then:
        resp.errors.size() == 1
        resp.errors[0].code == 'DENIED'
        resp.errors[0].message == 'Unauthenticated request. Unauthenticated requests do not have permission \"artifactregistry.repositories.downloadArtifacts\" on resource \"projects/wired-height-305919/locations/us-central1/repositories/fsdx-docker-dev\" (or it may not exist)'
    }
}
