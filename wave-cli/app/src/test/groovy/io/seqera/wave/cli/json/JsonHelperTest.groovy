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

package io.seqera.wave.cli.json

import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.cli.model.ContainerInspectResponseEx
import io.seqera.wave.core.spec.ContainerSpec
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
        json == '{"containerImage":"quay.io/nextflow/bash:latest","freeze":false}'
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
        def spec = new ContainerSpec('docker.io', 'https://docker.io', 'ubuntu', '22.04', 'sha:12345', null, null)
        def resp = new ContainerInspectResponseEx(spec)

        when:
        def result = JsonHelper.toJson(resp)
        then:
        result == '{"container":{"digest":"sha:12345","hostName":"https://docker.io","imageName":"ubuntu","reference":"22.04","registry":"docker.io"}}'
    }

}
