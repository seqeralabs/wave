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
import javax.annotation.Nullable

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.bind.annotation.Bindable
/**
 * Configuration to be used by a TokenService
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@ConfigurationProperties('wave.tokens')
interface TokenConfig {

    Cache getCache()

    @ConfigurationProperties('cache')
    interface Cache {

        @Bindable(defaultValue = "1h")
        @Nullable
        Duration getDuration()

        @Bindable(defaultValue = "10000")
        @Nullable
        int getMaxSize()

    }

}
