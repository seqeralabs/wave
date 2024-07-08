package io.seqera.wave.service.aws

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Value
import io.micronaut.objectstorage.InputStreamMapper
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.aws.AwsS3Configuration
import io.micronaut.objectstorage.aws.AwsS3Operations
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
class ObjectStorageOperationsFactory {

    @Value('${wave.build.logs.bucket}')
    String storageBucket

    @Singleton
    @Named("build-logs")
    ObjectStorageOperations<?, ?, ?> awsStorageOperations( @Named("awsS3Client") S3Client s3Client, InputStreamMapper inputStreamMapper) {
        AwsS3Configuration configuration = new AwsS3Configuration()
        configuration.setBucket(storageBucket)
        return new AwsS3Operations(configuration, s3Client, inputStreamMapper)
    }
}
