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

package io.seqera.wave.controller

import spock.lang.Specification

import io.micronaut.context.ApplicationContext

/**
 * Verify the legacy 'wave.allowAnonymous' key still drives the
 * 'wave.capabilities.anonymous-access' capability for backward compatibility.
 */
class AnonymousAccessLegacyAliasTest extends Specification {

    def 'legacy wave.allowAnonymous=#legacy resolves anonymous-access=#expected'() {
        given:
        def ctx = ApplicationContext.run(props)

        expect:
        ctx.getRequiredProperty('wave.capabilities.anonymous-access', Boolean) == expected

        cleanup:
        ctx.close()

        where:
        legacy  | props                          | expected
        'unset' | [:]                            | true      // shipped default
        'false' | ['wave.allowAnonymous': false] | false     // legacy override honored
        'true'  | ['wave.allowAnonymous': true]  | true
    }

}
