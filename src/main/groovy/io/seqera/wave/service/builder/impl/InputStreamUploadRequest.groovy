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

import io.micronaut.objectstorage.request.UploadRequest
/**
 * InputStreamUploadRequest is an implementation of UploadRequest that allows to upload data from an InputStream.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class InputStreamUploadRequest implements UploadRequest {

    private final InputStream inputStream
    private final String key
    private final String contentType
    private final Long contentLength

    InputStreamUploadRequest(InputStream inputStream, String key, String contentType, Long contentLength) {
        this.inputStream = inputStream
        this.key = key
        this.contentType = contentType
        this.contentLength = contentLength
    }

    @Override
    Optional<String> getContentType() {
        return Optional.ofNullable(contentType)
    }

    @Override
    String getKey() {
        return key
    }

    @Override
    Optional<Long> getContentSize() {
        return Optional.ofNullable(contentLength)
    }

    @Override
    InputStream getInputStream() {
        return inputStream
    }
}
