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

package io.seqera.wave.cli.util


import java.time.Instant

import io.seqera.wave.api.SubmitContainerTokenResponse
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
                buildId: '98765'
        )

        when:
        def result = YamlHelper.toYaml(resp)
        then:
        result == '''\
            buildId: '98765'
            containerImage: docker.io/some/container
            containerToken: '12345'
            expiration: '1970-01-20T13:57:19.913Z'
            targetImage: docker.io/some/repo
            '''.stripIndent(true)
    }

}
