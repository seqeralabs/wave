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

import io.micronaut.context.annotation.Property
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.persistence.CondaPackageRecord
import io.seqera.wave.service.persistence.PersistenceService
import jakarta.inject.Inject

/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
@Property(name = 'wave.packages.enabled', value = 'true')
class PackagesControllerTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    PersistenceService persistenceService

    final PREFIX = '/v1alpha1/packages'

    def setup() {
        def record1 = new CondaPackageRecord('bioconda', 'multiqc','0.4')
        def record2 = new CondaPackageRecord('seqera', 'multiqc','0.5')
        def record3 = new CondaPackageRecord('bioconda', 'salmon', '1.0')
        def record4 = new CondaPackageRecord('bioconda', 'salmon2',  '1.0')
        def record5 = new CondaPackageRecord('multiqc', 'multiqc','0.5')
        when:
        persistenceService.saveCondaPackagesChunks([record1, record2, record3, record4, record5], 2)
    }

    def'get correct packages list matches search'(){
        when:
        def resp = client.toBlocking().retrieve("$PREFIX/conda?search=multiqc")
        then:
        resp == '''
                {
                    "results":[
                        {"id":"seqera::multiqc=0.5","channel":"seqera","name":"multiqc","version":"0.5"},
                        {"id":"bioconda::multiqc=0.4","channel":"bioconda","name":"multiqc","version":"0.4"},
                        {"id":"multiqc::multiqc=0.5","channel":"multiqc","name":"multiqc","version":"0.5"}
                        ]
                    }
                '''.replaceAll(/\s|\n/, "")

        when:
        resp = client.toBlocking().retrieve("$PREFIX/conda?search=seqera::multiqc")
        then:
        resp == '{"results":[{"id":"seqera::multiqc=0.5","channel":"seqera","name":"multiqc","version":"0.5"}]}'

        when:
        resp = client.toBlocking().retrieve("$PREFIX/conda?search=bioconda::multiqc=0.4")
        then:
        resp == '{"results":[{"id":"bioconda::multiqc=0.4","channel":"bioconda","name":"multiqc","version":"0.4"}]}'

        when:
        resp = client.toBlocking().retrieve("$PREFIX/conda?search=multiqc=0.4")
        then:
        resp == '{"results":[{"id":"bioconda::multiqc=0.4","channel":"bioconda","name":"multiqc","version":"0.4"}]}'

        when:
        resp = client.toBlocking().retrieve("$PREFIX/conda?search=salmon2")
        then:
        resp == '{"results":[{"id":"bioconda::salmon2=1.0","channel":"bioconda","name":"salmon2","version":"1.0"}]}'

        when:
        resp = client.toBlocking().retrieve("$PREFIX/conda?search=bioconda::salmon=1")
        then:
        resp == '{"results":[{"id":"bioconda::salmon=1.0","channel":"bioconda","name":"salmon","version":"1.0"}]}'
    }

    def'get correct packages list matches search and in specific channels'(){
        when:
        def resp = client.toBlocking().retrieve("$PREFIX/conda?search=multiqc&channels=seqera,multiqc")
        then:
        resp == '''
                {
                    "results":[
                            {"id":"seqera::multiqc=0.5","channel":"seqera","name":"multiqc","version":"0.5"},
                            {"id":"multiqc::multiqc=0.5","channel":"multiqc","name":"multiqc","version":"0.5"}
                        ]
                }
                '''.replaceAll(/\s|\n/, "")
    }


}
