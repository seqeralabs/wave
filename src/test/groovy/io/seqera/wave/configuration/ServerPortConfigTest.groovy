/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.configuration

import spock.lang.Specification

import io.micronaut.context.ApplicationContext

class ServerPortConfigTest extends Specification {

    def 'should default server port to 9090'() {
        given:
        def ctx = ApplicationContext.run([:])

        expect:
        ctx.getProperty('micronaut.server.port', Integer).get() == 9090

        cleanup:
        ctx.close()
    }

    def 'should allow overriding server port via WAVE_SERVER_PORT env variable'() {
        given:
        def ctx = ApplicationContext.run([
            'WAVE_SERVER_PORT': '7070',
        ])

        expect:
        ctx.getProperty('micronaut.server.port', Integer).get() == 7070

        cleanup:
        ctx.close()
    }
}
