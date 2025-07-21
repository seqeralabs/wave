/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.service.builder.impl

import spock.lang.Specification
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class InputStreamUploadRequestTest extends Specification {

    def 'should return content type when it is set'() {
        given:
        def request = new InputStreamUploadRequest(Mock(InputStream), 'key', 'application/json', 123L)

        when:
        def contentType = request.getContentType()

        then:
        contentType.isPresent()
        contentType.get() == 'application/json'
    }

    def 'should return empty optional when content type is not set'() {
        given:
        def request = new InputStreamUploadRequest(Mock(InputStream), 'key', null, 123L)

        when:
        def contentType = request.getContentType()

        then:
        !contentType.isPresent()
    }

    def 'should return key when it is set'() {
        given:
        def request = new InputStreamUploadRequest(Mock(InputStream), 'key', 'application/json', 123L)

        when:
        def key = request.getKey()

        then:
        key == 'key'
    }

    def 'should return content size when it is set'() {
        given:
        def request = new InputStreamUploadRequest(Mock(InputStream), 'key', 'application/json', 123L)

        when:
        def contentSize = request.getContentSize()

        then:
        contentSize.isPresent()
        contentSize.get() == 123L
    }

    def 'should return empty optional when content size is not set'() {
        given:
        def request = new InputStreamUploadRequest(Mock(InputStream), 'key', 'application/json', null)

        when:
        def contentSize = request.getContentSize()

        then:
        !contentSize.isPresent()
    }

    def 'should return input stream when it is set'() {
        given:
        def inputStream = Mock(InputStream)
        def request = new InputStreamUploadRequest(inputStream, 'key', 'application/json', 123L)

        when:
        def resultStream = request.getInputStream()

        then:
        resultStream == inputStream
    }

    def 'should return empty metadata when none is set'() {
        given:
        def request = new InputStreamUploadRequest(Mock(InputStream), 'key', 'application/json', 123L)

        when:
        def metadata = request.getMetadata()

        then:
        metadata.isEmpty()
    }

}
