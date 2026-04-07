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

import java.time.Duration
import java.time.Instant

import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildStatusResponseTest extends Specification {

    def 'should validate equals and hashcode' () {
        given:
        def n = Instant.now()
        def d = Duration.ofMinutes(1)
        def c1 = new BuildStatusResponse('123', BuildStatusResponse.Status.PENDING, n, d, true)
        def c2 = new BuildStatusResponse('123', BuildStatusResponse.Status.PENDING, n, d, true)
        def c3 = new BuildStatusResponse('321', BuildStatusResponse.Status.PENDING, n, d, true)

        expect:
        c1 == c2
        c1 != c3
        and:
        c1.hashCode() == c2.hashCode()
        c1.hashCode() != c3.hashCode()
    }

    def 'should validate creation' () {
        given:
        def n = Instant.now()
        def d = Duration.ofMinutes(1)
        def c1 = new BuildStatusResponse('123', BuildStatusResponse.Status.PENDING, n, d, true)

        expect:
        c1.id == '123'
        c1.status == BuildStatusResponse.Status.PENDING
        c1.startTime == n
        c1.duration == d
        c1.succeeded == true
    }

    def 'should create response object' () {
        given:
        def ts = Instant.now()
        def resp = new BuildStatusResponse(
                'test',
                BuildStatusResponse.Status.PENDING,
                ts,
                Duration.ofMinutes(1),
                true,
        )

        expect:
        resp.id == "test"
        resp.status == BuildStatusResponse.Status.PENDING
        resp.startTime == ts
        resp.duration == Duration.ofMinutes(1)
        resp.succeeded
    }
}
