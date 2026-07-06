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

package io.seqera.wave.tower

import groovy.transform.CompileStatic

/**
 * User preference controlling when Wave sends a build completion email.
 *
 * <ul>
 *   <li>{@code ALWAYS_ON}  &mdash; notify on every build outcome (success and failure)</li>
 *   <li>{@code ON_ERROR}   &mdash; notify only when the build fails</li>
 *   <li>{@code ALWAYS_OFF} &mdash; never notify</li>
 * </ul>
 *
 * The value is provided by Tower in the user-info payload. A {@code null} value
 * (older Tower clients that don't send the field, or a value not known to Wave)
 * is treated as {@link #ALWAYS_ON}, preserving the historical behaviour.
 */
@CompileStatic
enum WaveBuildNotification {
    ALWAYS_ON,
    ON_ERROR,
    ALWAYS_OFF
}
