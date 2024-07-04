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
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.BlobCacheConfig
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
/**
 * Factory implementation for S3Client
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Factory
@CompileStatic
@Slf4j
@Requires(property = 'wave.blobCache.enabled', value = 'true')
class S3ClientFactory {

    @Inject
    private BlobCacheConfig blobConfig

    @Singleton
    @Named('BlobS3Client')
    S3Client cloudflareS3Client() {
        final creds = AwsBasicCredentials.create(blobConfig.storageAccessKey, blobConfig.storageSecretKey)
        final builder = S3Client.builder()
                    .region(Region.of(blobConfig.storageRegion))
                    .credentialsProvider(StaticCredentialsProvider.create(creds))

        if (blobConfig.storageEndpoint) {
            builder.endpointOverride(URI.create(blobConfig.storageEndpoint))
        }

        log.info("Creating S3 client with configuration: $builder")
        return builder.build()
    }
}
