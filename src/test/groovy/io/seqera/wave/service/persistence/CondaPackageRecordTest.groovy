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
package io.seqera.wave.service.persistence

import spock.lang.Specification

import io.seqera.wave.util.JacksonHelper

/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */

class CondaPackageRecordTest extends Specification {
    def 'should create conda package record'() {

        given:
        def record = new CondaPackageRecord('channel', 'name', 'version', ['build1', 'build2'])
        expect:
        record.toString() == "{id:\"channel::name=version\", channel:\"channel\", name:\"name\", version:\"version\", builds:['build1', 'build2'] }"

    }

    def 'should serialise-deserialize conda package record'() {
        given:
        def record = new CondaPackageRecord('channel', 'name', 'version', ['build1', 'build2'])

        when:
        def json = JacksonHelper.fromJson(JacksonHelper.toJson(record), CondaPackageRecord)
        then:
        json == record
    }
}
