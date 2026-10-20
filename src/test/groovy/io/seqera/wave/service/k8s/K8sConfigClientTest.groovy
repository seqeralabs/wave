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

package io.seqera.wave.service.k8s

import spock.lang.Specification

import java.nio.file.Files
import java.time.Duration

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class K8sConfigClientTest extends Specification {

    def 'should create api client with read timeout'() {
        given:
        def folder = Files.createTempDirectory('test')
        def config = folder.resolve('kubeconfig')
        config.text = '''\
            apiVersion: v1
            kind: Config
            current-context: test
            clusters:
            - name: test
              cluster:
                server: https://localhost:6443
            contexts:
            - name: test
              context:
                cluster: test
                user: test
            users:
            - name: test
              user:
                token: foo
            '''.stripIndent()
        and:
        def client = new K8sConfigClient(kubeConfigPath: config.toString(), readTimeout: Duration.ofSeconds(45))

        when:
        def api = client.apiClient()
        then:
        api.readTimeout == 45_000

        cleanup:
        folder?.deleteDir()
    }
}
