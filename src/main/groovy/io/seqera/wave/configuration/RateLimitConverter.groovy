package io.seqera.wave.configuration

import java.time.Duration

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Factory
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.TypeConverter
import jakarta.inject.Singleton


/**
 * A factory to convert String to LimitConfigS
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Factory
@CompileStatic
class RateLimitConverter {

    @Singleton
    TypeConverter<String, LimitConfig> stringToLimitConfig(){
        return (String object, targetType, context) -> {
            String[] fields = object.split("/")
            assert fields.size() == 2
            assert fields.first().isNumber()
            int max = fields.first() as int
            Duration duration = ConversionService.SHARED.convert(fields.last(), Duration).orElseThrow()
            return Optional.of(new LimitConfig(max:max, duration:duration))
        } as TypeConverter<String, LimitConfig>
    }

}
