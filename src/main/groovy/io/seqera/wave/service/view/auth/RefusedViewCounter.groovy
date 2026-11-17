/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.service.view.auth

import groovy.transform.CompileStatic
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Requires
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Counts view pages refused, by endpoint and outcome.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Singleton
@Requires(bean = MeterRegistry)
@CompileStatic
class RefusedViewCounter {

    @Inject
    private MeterRegistry registry

    void increment(String towerEndpoint, String outcome) {
        registry.counter('wave.view.refused',
                'endpoint', towerEndpoint ?: 'none',
                'outcome', outcome ?: 'unknown').increment()
    }

}
