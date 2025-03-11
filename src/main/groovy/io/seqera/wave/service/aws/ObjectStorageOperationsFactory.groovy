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

import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.objectstorage.InputStreamMapper
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.aws.AwsS3Configuration
import io.micronaut.objectstorage.aws.AwsS3Operations
import io.micronaut.objectstorage.local.LocalStorageConfiguration
import io.micronaut.objectstorage.local.LocalStorageOperations
import io.seqera.wave.util.BucketTokenizer
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import software.amazon.awssdk.services.s3.S3Client
/**
 * Factory implementation for ObjectStorageOperations
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Factory
@CompileStatic
@Slf4j
@Requires(property = 'wave.build.logs.bucket')
class ObjectStorageOperationsFactory {

    @Value('${wave.build.logs.bucket}')
    private String storageBucket

    @Inject
    private ApplicationContext context

    @Singleton
    @Named("build-logs")
    ObjectStorageOperations<?, ?, ?> createStorageOps(@Named("DefaultS3Client") S3Client s3Client, InputStreamMapper inputStreamMapper) {
        final scheme = storageBucket ? BucketTokenizer.from(storageBucket)?.getScheme() : null
        if( !scheme ) {
            return localFactory(storageBucket, inputStreamMapper)
        }
        if( scheme=='s3' ) {
            return awsFactory(storageBucket, inputStreamMapper)
        }
        throw new IllegalArgumentException("Unsupported storage scheme: '$scheme' - offneding setting 'wave.build.logs.bucket': ${storageBucket}" )
    }

    protected ObjectStorageOperations<?, ?, ?> localFactory(String storageBucket, InputStreamMapper inputStreamMapper) {
        final localPath = Path.of(storageBucket)
        log.info "Using local build logs path: $localPath"
        LocalStorageConfiguration configuration = new LocalStorageConfiguration('build-logs')
        configuration.setPath(localPath)
        return new LocalStorageOperations(configuration)
    }

    protected ObjectStorageOperations<?, ?, ?> awsFactory(String storageBucket, InputStreamMapper inputStreamMapper) {
        final s3Client = context.getBean(S3Client.class, Qualifiers.byName("DefaultS3Client"));
        AwsS3Configuration configuration = new AwsS3Configuration('build-logs')
        configuration.setBucket(storageBucket)
        return new AwsS3Operations(configuration, s3Client, inputStreamMapper)
    }
}
