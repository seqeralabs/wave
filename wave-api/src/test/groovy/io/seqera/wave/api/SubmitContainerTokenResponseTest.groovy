/*
 * Copyright 2025, Seqera Labs
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

package io.seqera.wave.api

import java.time.Instant

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SubmitContainerTokenResponseTest extends Specification {

    def 'should create response' () {
        when:
        def timestamp = Instant.now().plusSeconds(10)
        and:
        def resp = new SubmitContainerTokenResponse(
                '123',
                '456',
                'target',
                timestamp,
                'container/x',
                'build-1234',
                true,
                true,
                true,
                'scan-1234',
                true
        )
        then:
        resp.requestId == '123'
        resp.containerToken == '456'
        resp.targetImage == 'target'
        resp.expiration == timestamp
        resp.containerImage == 'container/x'
        resp.buildId == 'build-1234'
        resp.cached
        resp.freeze
        resp.mirror
        resp.scanId == 'scan-1234'
        resp.succeeded == true
    }

    def 'should validate equals & hashCode' () {
        given:
        def ts = Instant.now().plusSeconds(10)
        and:
        def r1 = new SubmitContainerTokenResponse('123', '345', 'target', ts, 'container/x', 'build-xyz', false, false, false, 'scan-abc', false)
        def r2 = new SubmitContainerTokenResponse('123', '345', 'target', ts, 'container/x', 'build-xyz', false, false, false, 'scan-abc', false)
        def r3 = new SubmitContainerTokenResponse('abc', '345', 'target', ts, 'container/x', 'build-xyz', false, false, false, 'scan-abc', true)

        expect:
        r1 == r2
        r1 != r3
        and:
        r1.hashCode() == r2.hashCode()
        r1.hashCode() != r3.hashCode()
    }

}
