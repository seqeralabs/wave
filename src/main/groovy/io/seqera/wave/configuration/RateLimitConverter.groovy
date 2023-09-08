/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

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
