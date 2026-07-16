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

package io.seqera.wave.controller

import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 * Verify that the /view/** pages are served when 'wave.views.enabled' is true.
 *
 * @author Gavin Elder
 */
@Property(name = 'wave.server.url', value = 'http://foo.com')
@Property(name = 'wave.views.enabled', value = 'true')
@MicronautTest
class ViewControllerEnabledTest extends Specification {

    @Inject
    ApplicationContext applicationContext

    def 'should load the view controller when views are enabled'() {
        expect:
        applicationContext.containsBean(ViewController)
    }

}
