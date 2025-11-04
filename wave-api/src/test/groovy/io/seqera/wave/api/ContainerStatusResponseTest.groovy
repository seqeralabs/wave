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
class ContainerStatusResponseTest extends Specification {

    def 'should create a status response' () {
        when:
        def ts = Instant.now()
        def resp = new ContainerStatusResponse(
                'id-1',
                ContainerStatus.PENDING,
                'build-1',
                'mirror-2',
                'scan-3',
                [medium:1, high:2],
                true,
                "Something failed",
                "https://foo.com",
                ts,
                Duration.ofMinutes(2),
        )
        
        then:
        resp.id == 'id-1'
        resp.status == ContainerStatus.PENDING
        resp.buildId == 'build-1'
        resp.mirrorId == 'mirror-2'
        resp.scanId == 'scan-3'
        resp.vulnerabilities == [medium:1, high:2]
        resp.succeeded == true
        resp.reason == "Something failed"
        resp.detailsUri == "https://foo.com"
        resp.creationTime == ts
        resp.duration == Duration.ofMinutes(2)
    }

}
