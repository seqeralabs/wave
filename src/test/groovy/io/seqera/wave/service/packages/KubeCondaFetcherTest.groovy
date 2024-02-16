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

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.configuration.PackagesConfig
import io.seqera.wave.service.k8s.K8sService
import jakarta.inject.Inject

/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */

@MicronautTest
class KubeCondaFetcherTest extends Specification {

    @Inject
    PackagesConfig config

    def 'should throw IllegalStateException when conda fetcher fails'() {
        given:
        def k8sService = Mock(K8sService)
        and:
        k8sService.waitPod(_, _) >> null
        and:
        def fetcher = new KubeCondaFetcher( k8sService: k8sService, config: config)

        when:
        fetcher.run(['command'], Path.of('/some/conda/dir'))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Conda fetcher failed - logs: null'
    }
}
