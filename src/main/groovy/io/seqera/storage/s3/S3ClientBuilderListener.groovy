package io.seqera.storage.s3

import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.seqera.config.S3StorageConfiguration
import jakarta.inject.Singleton
import software.amazon.awssdk.services.s3.S3ClientBuilder

@Requires(property = "towerreg.storage.s3.endpoint")
@Singleton
class S3ClientBuilderListener implements BeanCreatedEventListener<S3ClientBuilder> {

    String endpoint

    S3ClientBuilderListener(S3StorageConfiguration s3StorageConfiguration){
        this.endpoint = s3StorageConfiguration.endpoint.get()
    }

    @Override
    S3ClientBuilder onCreated(BeanCreatedEvent<S3ClientBuilder> event) {
        S3ClientBuilder builder = event.getBean();
        builder.endpointOverride(endpoint.toURI())
        return builder;
    }
}
