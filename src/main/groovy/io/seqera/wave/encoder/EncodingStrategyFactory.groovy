package io.seqera.wave.encoder

import java.lang.reflect.Type

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.storage.DigestStore
import jakarta.inject.Singleton


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Singleton
class EncodingStrategyFactory {

    EncodingStrategy createEncoding(Class clazz){
        switch (clazz){
            case BuildResult:
                return new MoshiEncodeStrategy<BuildResult>(){}
        }
        throw new RuntimeException("No encode strategey for $clazz")
    }

}
