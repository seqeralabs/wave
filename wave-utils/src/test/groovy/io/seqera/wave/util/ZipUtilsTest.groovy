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

package io.seqera.wave.util

import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ZipUtilsTest extends Specification {

    def 'should compress/decompress a text'() {
        given:
        def TEXT = 'Hello world\n' * 10

        when:
        def buffer = ZipUtils.compress(TEXT)
        then:
        buffer.size() > 0 && buffer.size() < TEXT.bytes.size()

        when:
        def copy = ZipUtils.decompressAsString(buffer)
        then:
        copy == TEXT

        when:
        def bytes = ZipUtils.decompressAsBytes(buffer)
        then:
        bytes == TEXT.bytes
    }

}
