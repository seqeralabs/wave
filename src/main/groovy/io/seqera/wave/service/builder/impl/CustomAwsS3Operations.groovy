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

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Replaces
import io.micronaut.core.annotation.NonNull
import io.micronaut.objectstorage.InputStreamMapper
import io.micronaut.objectstorage.aws.AwsS3Configuration
import io.micronaut.objectstorage.aws.AwsS3Operations
import io.micronaut.objectstorage.request.BytesUploadRequest
import io.micronaut.objectstorage.request.FileUploadRequest
import io.micronaut.objectstorage.request.UploadRequest
import jakarta.inject.Singleton
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
/**
 * CustomAwsS3Operations is a custom implementation of AwsS3Operations that overrides the getRequestBody method
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@EachBean(AwsS3Configuration)
@CompileStatic
@Singleton
@Replaces(AwsS3Operations)
class CustomAwsS3Operations extends AwsS3Operations{

    private final InputStreamMapper inputStreamMapper;

    /**
     *
     * @param configuration AWS S3 Configuration
     * @param s3Client S3 Client
     * @param inputStreamMapper InputStream Mapper
     */
    CustomAwsS3Operations(AwsS3Configuration configuration, S3Client s3Client, InputStreamMapper inputStreamMapper) {
        super(configuration, s3Client, inputStreamMapper)
    }

    /**
     * @param uploadRequest the upload request
     * @return An AWS' {@link software.amazon.awssdk.core.sync.RequestBody} from a Micronaut's {@link io.micronaut.objectstorage.request.UploadRequest}.
     */
    @NonNull
    @Override
    protected RequestBody getRequestBody(@NonNull UploadRequest uploadRequest) {
        if (uploadRequest instanceof FileUploadRequest) {
            FileUploadRequest request = (FileUploadRequest) uploadRequest;
            return RequestBody.fromFile(request.getFile());
        } else if (uploadRequest instanceof BytesUploadRequest) {
            BytesUploadRequest request = (BytesUploadRequest) uploadRequest;
            return RequestBody.fromBytes(request.getBytes());
        } else if (uploadRequest instanceof InputStreamUploadRequest) {
            InputStreamUploadRequest request = (InputStreamUploadRequest) uploadRequest;
            return RequestBody.fromInputStream(request.getInputStream(), request.getContentSize().orElse(0L));
        } else {
            byte[] inputBytes = inputStreamMapper.toByteArray(uploadRequest.getInputStream());
            return RequestBody.fromBytes(inputBytes);
        }
    }

}
