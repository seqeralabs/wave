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

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

/**
 * Marker object only accessible when data retention is enabled. Services can declare it as a
 * requirement to enable or disable the corresponding behaviour.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
@Singleton
@Requires(property = 'wave.retention.enabled', value = 'true', defaultValue = 'false')
class RetentionEnabled {
}
