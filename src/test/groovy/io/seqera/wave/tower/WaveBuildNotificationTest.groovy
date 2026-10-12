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

package io.seqera.wave.tower

import io.seqera.wave.util.JacksonHelper
import spock.lang.Specification

class WaveBuildNotificationTest extends Specification {

    def 'should define three constants'() {
        expect:
        WaveBuildNotification.values().length == 3
        WaveBuildNotification.valueOf('ALWAYS_ON')  == WaveBuildNotification.ALWAYS_ON
        WaveBuildNotification.valueOf('ON_ERROR')   == WaveBuildNotification.ON_ERROR
        WaveBuildNotification.valueOf('ALWAYS_OFF') == WaveBuildNotification.ALWAYS_OFF
    }

    def 'an unknown enum value deserializes to null instead of failing the payload'() {
        when:
        def user = JacksonHelper.fromJson('{"id":1,"userName":"alice","email":"a@b.c","waveBuildNotification":"SOME_FUTURE_VALUE"}', User)
        then:
        noExceptionThrown()
        user.waveBuildNotification == null
    }
}
