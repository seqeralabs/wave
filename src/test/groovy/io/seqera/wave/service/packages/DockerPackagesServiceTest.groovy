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

package io.seqera.wave.service.packages

import spock.lang.Specification

import java.nio.file.Path

import io.micronaut.context.ApplicationContext

/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class DockerPackagesServiceTest extends Specification {
    def 'should get docker command' () {
        given:
        def props = ['wave.package.conda.image.name': 'condaImage']
        and:
        def ctx = ApplicationContext.run(props)
        and:
        def fetcher = ctx.getBean(DockerPackagesService)
        when:
        def command = fetcher.dockerWrapper(Path.of('/some/conda/dir'))

        then:
        command == [
                'docker',
                'run',
                '--rm',
                '-w',
                '/some/conda/dir',
                '-v',
                '/some/conda/dir:/some/conda/dir:rw',
                'condaImage'
        ]
    }
}
