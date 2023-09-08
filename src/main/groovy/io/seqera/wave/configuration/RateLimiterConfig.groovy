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
