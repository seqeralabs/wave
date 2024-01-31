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

package io.seqera.wave.util

import spock.lang.Specification

import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildFormat

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SpackHelperTest extends Specification {

    def 'should load builder template' () {
        expect:
        SpackHelper.builderDockerTemplate().startsWith('# Builder image')
    }

    def 'should prepend builder template' () {
        expect:
        SpackHelper.prependBuilderTemplate('foo', BuildFormat.DOCKER).startsWith('# Builder image')
        SpackHelper.prependBuilderTemplate('foo', BuildFormat.SINGULARITY).endsWith('\nfoo')
    }
}
