/*
 * Copyright 2023-2025, Seqera Labs
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

package io.seqera.wave.cli.json

import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.cli.model.ContainerInspectResponseEx
import io.seqera.wave.core.spec.ContainerSpec
import io.seqera.wave.core.spec.ManifestSpec
import io.seqera.wave.core.spec.ObjectRef
import spock.lang.Specification
/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class JsonHelperTest extends Specification {

    def 'should encode request' () {
        given:
        def req = new SubmitContainerTokenRequest(containerImage: 'quay.io/nextflow/bash:latest')
        when:
        def json = JsonHelper.toJson(req)
        then:
        json == '{"containerImage":"quay.io/nextflow/bash:latest","freeze":false,"mirror":false}'
    }

    def 'should decode request' () {
        given:
        def REQ = '{"containerImage":"quay.io/nextflow/bash:latest","freeze":false}'
        when:
        def result = JsonHelper.fromJson(REQ, SubmitContainerTokenRequest)
        then:
        result.containerImage == 'quay.io/nextflow/bash:latest'
    }

    def 'should convert response to json' () {
        given:
        def layers = [new ObjectRef('text', 'sha256:12345', 100, null), new ObjectRef('text', 'sha256:67890', 200, null) ]
        def manifest = new ManifestSpec(2, 'some/media', null, layers, [one: '1', two:'2'])
        def spec = new ContainerSpec('docker.io', 'https://docker.io', 'ubuntu', '22.04', 'sha:12345', null, manifest)
        def resp = new ContainerInspectResponseEx(spec)

        when:
        def result = JsonHelper.toJson(resp)
        then:
        result == '''\
                    {
                        "container":{   
                            "digest":
                            "sha:12345","hostName":"https://docker.io",
                            "imageName":"ubuntu",
                            "manifest":{
                            "annotations":{"one":"1","two":"2"},
                            "layers":[
                                {"digest":"sha256:12345",
                                "mediaType":"text",
                                "size":100,
                                "uri":"https://docker.io/v2/ubuntu/blobs/sha256:12345"},
                                {"digest":"sha256:67890",
                                "mediaType":"text",
                                "size":200,
                                "uri":"https://docker.io/v2/ubuntu/blobs/sha256:67890"}
                                ],
                            "mediaType":"some/media",
                            "schemaVersion":2
                            },
                            "reference":"22.04",
                            "registry":"docker.io"
                        }
                    }
                '''.replaceAll("\\s+", "").trim()
    }

}
