/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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

package io.seqera.wave.service.blob.impl

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.service.blob.BlobCacheClient
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
/**
 * Implements a client to interact with the blob cache
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@Requires(property = 'wave.blobCache.enabled', value = 'true')
@CompileStatic
class BlobCacheClientImpl implements BlobCacheClient{

    @Inject
    @Named('BlobS3Client')
    private S3Client s3Client

    @Inject
    private BlobCacheConfig blobConfig

    @Override
    Long getBlobSize(String key) {
        try {
            def headObjectRequest =
                    HeadObjectRequest.builder()
                            .bucket(blobConfig.storageBucket)
                            .key(key)
                            .build()
            def headObjectResponse = s3Client.headObject(headObjectRequest)

            Long contentLength = headObjectResponse.contentLength()
            return contentLength ?: 0L
        }catch (Exception e){
            log.error("Error getting content length of object $key from bucket ${blobConfig.storageBucket}", e)
            return 0L
        }
    }

    @Override
    void deleteBlob(String key) {
        try {
            def deleteObjectRequest =
                    DeleteObjectRequest.builder()
                            .bucket(blobConfig.storageBucket)
                            .key(key)
                            .build()
            s3Client.deleteObject(deleteObjectRequest)
            log.debug("Deleted object $key from bucket ${blobConfig.storageBucket}")
        }catch (Exception e){
            log.error("Error deleting object $key from bucket ${blobConfig.storageBucket}", e)
        }
    }
}
