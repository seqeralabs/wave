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
package io.seqera.wave.service.aws

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Factory
import io.seqera.wave.configuration.BlobCacheConfig
import jakarta.inject.Inject
import jakarta.inject.Singleton
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.presigner.S3Presigner
/**
 * Factory implementation for S3Presigner
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */

@Factory
@CompileStatic
class AwsS3PresignerFactory {

    @Inject
    private BlobCacheConfig blobConfig

    @Singleton
    S3Presigner s3presigner() {
        final builder = S3Presigner.builder()
                .region(Region.of(blobConfig.storageRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
        if ( blobConfig.storageEndpoint ) {
            builder.endpointOverride(URI.create(blobConfig.storageEndpoint))
        }
        return builder.build()
    }
}
