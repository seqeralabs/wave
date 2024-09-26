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

package io.seqera.wave.service.buildstatus

import java.time.Duration
import java.time.Instant

import io.seqera.wave.api.BuildStatusResponse
import io.seqera.wave.api.ScanMode

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface BuildStatusService {

    interface StatusInfo {
        String getBuildId()
        String getScanId()
        ScanMode getScanMode()
        Instant getStartTime()
        Duration getDuration()
        Boolean getSucceeded()
    }

    BuildStatusResponse getBuildStatus(String requestId)

}
