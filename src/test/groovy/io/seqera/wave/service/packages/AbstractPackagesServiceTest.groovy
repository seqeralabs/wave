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

import java.nio.file.Files

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.persistence.PersistenceService
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class AbstractPackagesServiceTest extends Specification {

    @Inject
    DockerPackagesService fetcher

    @Inject
    PersistenceService persistenceService

    def 'should parse conda file' () {
        given:
        def folder = Files.createTempDirectory('test')
        def file = folder.resolve('conda.txt')
        file.text = '''\
Loading channels: ...working... done
# Name                       Version           Build  Channel             
multiqc                          0.4          py27_0  bioconda            
multiqc                          0.4          py35_0  bioconda            
multiqc                          0.5          py27_0  bioconda            
multiqc                          0.5          py35_0  bioconda            
multiqc                          0.6          py34_0  bioconda
salmon                           1.1          py34_0  bioconda
salmon                           1.2          py34_0  bioconda    
'''
        and:

        when:
        fetcher.processResult(file)
        then:
        persistenceService.findCondaPackage(null, null).size() == 5
        and:
        persistenceService.findCondaPackage('multiqc', null).size() == 3
        persistenceService.findCondaPackage('salmon', null).size() == 2
        persistenceService.findCondaPackage('bioconda', null).size() == 5
        persistenceService.findCondaPackage(null, ['bioconda']).size() == 5

        cleanup:
        folder?.deleteDir()
    }

}
