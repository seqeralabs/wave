/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.cli.json

import io.seqera.wave.api.SubmitContainerTokenRequest
import spock.lang.Specification;

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


}
