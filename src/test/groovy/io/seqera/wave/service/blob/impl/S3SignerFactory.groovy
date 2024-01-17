package io.seqera.wave.service.blob.impl

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.aws.AwsS3PresignerFactory
import io.seqera.wave.test.AwsS3TestContainer
import jakarta.inject.Singleton
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.presigner.S3Presigner
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(env = 'test')
@Factory
@Replaces(AwsS3PresignerFactory)
class S3SignerFactory implements AwsS3TestContainer {

    @Singleton
    S3Presigner create() {

        return S3Presigner.builder()
                .region(Region.of('eu-west1'))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .endpointOverride(URI.create("https://${getAwsS3HostName()}:${getAwsS3Port()}"))
                .build()

    }


}
