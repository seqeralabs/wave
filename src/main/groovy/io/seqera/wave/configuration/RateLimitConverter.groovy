/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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
