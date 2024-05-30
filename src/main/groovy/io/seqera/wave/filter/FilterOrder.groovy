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

package io.seqera.wave.filter

/**
 * Define the order of HTTP filters. The smaller number has higher priority
 *
 * {@link io.micronaut.core.order.Ordered}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface FilterOrder {

    final int DENY_PATHS = -100
    final int RATE_LIMITER = -50
    final int PULL_METRICS = 10

}
