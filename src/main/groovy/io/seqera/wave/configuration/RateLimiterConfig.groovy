/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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
import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.annotation.Bindable

/**
 * Model Rate limiter configuration
 * 
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Requires(env = 'rate-limit')
@ConfigurationProperties('rate-limit')
@Context
@CompileStatic
interface RateLimiterConfig {

    BuildLimit getBuild()

    @ConfigurationProperties('build')
    static interface BuildLimit {

        @Bindable("10 / 1h")
        LimitConfig getAnonymous()

        @Bindable("10 / 1m")
        LimitConfig getAuthenticated()
    }

    RequestLimit getPull()

    @ConfigurationProperties('pull')
    static interface RequestLimit {

        @Bindable("100 / 1h")
        LimitConfig getAnonymous()

        @Bindable("100 / 1m")
        LimitConfig getAuthenticated()
    }

}
