/*
 * Copyright 2023, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.wave.cli.util

import java.time.Instant

import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.cli.model.ContainerInspectResponseEx
import io.seqera.wave.core.spec.ContainerSpec
import io.seqera.wave.core.spec.ManifestSpec
import io.seqera.wave.core.spec.ObjectRef
import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class YamlHelperTest extends Specification {

    def 'should convert container response' () {
        given:
        def resp = new SubmitContainerTokenResponse(
                containerToken: "12345",
                targetImage: 'docker.io/some/repo',
                containerImage: 'docker.io/some/container',
                expiration: Instant.ofEpochMilli(1691839913),
                buildId: '98765',
                cached: false,
                freeze: false
        )

        when:
        def result = YamlHelper.toYaml(resp)
        then:
        result == '''\
            buildId: '98765'
            cached: false
            containerImage: docker.io/some/container
            containerToken: '12345'
            expiration: '1970-01-20T13:57:19.913Z'
            freeze: false
            targetImage: docker.io/some/repo
            '''.stripIndent(true)
    }

    def 'should convert response to yaml' () {
        given:
        def layers = [ new ObjectRef('text', 'sha256:12345', 100, null), new ObjectRef('text', 'sha256:67890', 200, null) ]
        def manifest = new ManifestSpec(2, 'some/media', null, layers, [one: '1', two:'2'])
        def spec = new ContainerSpec('docker.io', 'https://docker.io', 'ubuntu','22.04','sha:12345', null, manifest)
        def resp = new ContainerInspectResponseEx(spec)

        when:
        def result = YamlHelper.toYaml(resp)
        then:
        result == '''\
            container:
              config: null
              digest: sha:12345
              hostName: https://docker.io
              imageName: ubuntu
              manifest:
                annotations:
                  one: '1'
                  two: '2'
                config: null
                layers:
                - annotations: null
                  digest: sha256:12345
                  mediaType: text
                  size: 100
                  uri: https://docker.io/v2/ubuntu/blobs/sha256:12345
                - annotations: null
                  digest: sha256:67890
                  mediaType: text
                  size: 200
                  uri: https://docker.io/v2/ubuntu/blobs/sha256:67890
                mediaType: some/media
                schemaVersion: 2
              reference: '22.04'
              registry: docker.io
            '''.stripIndent(true)
    }

}
