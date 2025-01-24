/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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

package io.seqera.wave.controller

import spock.lang.Specification

import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus

/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class ServiceInfoControllerTest extends Specification {

    def 'should redirect to /openapi/'() {
        given:
        def controller = new ServiceInfoController()

        when:
        HttpResponse response = controller.getOpenAPI()

        then:
        response.status == HttpStatus.MOVED_PERMANENTLY
        response.header('Location') == '/openapi/'
    }

}
