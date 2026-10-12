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
class BuildCompressionTest extends Specification {

    def 'should set compression mode' () {
        expect:
        new BuildCompression().getMode() == null
        and:
        new BuildCompression().withMode(BuildCompression.Mode.gzip).getMode() == BuildCompression.Mode.gzip
        new BuildCompression().withMode(BuildCompression.Mode.zstd).getMode() == BuildCompression.Mode.zstd
        new BuildCompression().withMode(BuildCompression.Mode.estargz).getMode() == BuildCompression.Mode.estargz
    }

    def 'should set compression level'() {
        expect:
        new BuildCompression().getLevel()==null
        and:
        new BuildCompression().withLevel(1).getLevel() == 1
        new BuildCompression().withLevel(10).getLevel() == 10
    }

    def 'should set compression force'() {
        expect:
        new BuildCompression().getForce() == null
        and:
        new BuildCompression().withForce(false).getForce() == false
        new BuildCompression().withForce(true).getForce() == true
    }

}
