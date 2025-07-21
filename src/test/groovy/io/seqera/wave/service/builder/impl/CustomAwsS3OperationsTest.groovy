/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2025, Seqera Labs
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

import io.micronaut.objectstorage.InputStreamMapper
import io.micronaut.objectstorage.aws.AwsS3Configuration
import software.amazon.awssdk.services.s3.S3Client
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class CustomAwsS3OperationsTest extends Specification {

    def 'should create RequestBody from input stream upload request with content size'() {
        given:
        def inputStream = new ByteArrayInputStream('Stream content'.bytes)
        def uploadRequest = new InputStreamUploadRequest(inputStream, 'key', 'application/json', 12L)
        def operations = new CustomAwsS3Operations(Mock(AwsS3Configuration), Mock(S3Client), Mock(InputStreamMapper))

        when:
        def requestBody = operations.getRequestBody(uploadRequest)

        then:
        requestBody.optionalContentLength().get() == 12L
        requestBody.contentStreamProvider().name() == 'Stream'
    }

    def 'should create RequestBody from input stream upload request without content size'() {
        given:
        def inputStream = new ByteArrayInputStream('Stream content'.bytes)
        def uploadRequest = new InputStreamUploadRequest(inputStream, 'key', 'application/json', null)
        def inputStreamMapper = Mock(InputStreamMapper)
        inputStreamMapper.toByteArray(inputStream) >> 'Stream content'.bytes
        def operations = new CustomAwsS3Operations(Mock(AwsS3Configuration), Mock(S3Client), inputStreamMapper)

        when:
        def requestBody = operations.getRequestBody(uploadRequest)

        then:
        requestBody.optionalContentLength().get() == 0
        requestBody.contentStreamProvider().name() == 'Stream'
    }
}
