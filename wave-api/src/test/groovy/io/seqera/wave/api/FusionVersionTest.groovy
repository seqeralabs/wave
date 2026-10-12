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

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class FusionVersionTest extends Specification {

    def 'should parse version from uri' () {
        expect:
        FusionVersion.from(STR) == EXPECTED

        where:
        EXPECTED                                | STR
        null                                    | null
        new FusionVersion('2','amd64')          | 'https://foo.com/v2-amd64.json'
        new FusionVersion('22','amd64')         | 'https://foo.com/v22-amd64.json'
        new FusionVersion('2.1','amd64')        | 'https://foo.com/v2.1-amd64.json'
        new FusionVersion('2.11','amd64')       | 'https://foo.com/v2.11-amd64.json'
        new FusionVersion('2.1.3','amd64')      | 'https://foo.com/v2.1.3-amd64.json'
        new FusionVersion('2.1.3','arm64')      | 'https://foo.com/v2.1.3-arm64.json'
        new FusionVersion('2.1.33','arm64')     | 'https://foo.com/v2.1.33-arm64.json'
        new FusionVersion('2.1.3a','arm64')     | 'https://foo.com/v2.1.3a-arm64.json'
        new FusionVersion('2.1.3.a','arm64')    | 'https://foo.com/v2.1.3.a-arm64.json'
        new FusionVersion('2.2.8','amd64')      | 'https://foo.com/releases/pkg/2/2/8/fusion-amd64.tar.gz'
        new FusionVersion('2.2.8','arm64')      | 'https://foo.com/releases/pkg/2/2/8/fusion-arm64.tar.gz'
        new FusionVersion('2.2','arm64')        | 'https://foo.com/releases/pkg/2/2/fusion-arm64.tar.gz'
        new FusionVersion('2.22','arm64')       | 'https://foo.com/releases/pkg/2/22/fusion-arm64.tar.gz'
        new FusionVersion('2.2.33','arm64')     | 'https://foo.com/releases/pkg/2/2/33/fusion-arm64.tar.gz'
        new FusionVersion('2.2.8.a','arm64')    | 'https://foo.com/releases/pkg/2/2/8/a/fusion-arm64.tar.gz'
    }

}
