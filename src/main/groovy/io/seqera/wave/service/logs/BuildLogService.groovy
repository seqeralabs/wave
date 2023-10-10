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

package io.seqera.wave.service.logs

import groovy.transform.Canonical
import io.micronaut.http.server.types.files.StreamedFile

/**
 * Service to manage logs
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
interface BuildLogService {

    @Canonical
    class BuildLog {
        String data
        boolean truncated
    }

    void storeLog(String buildId, String log)

    StreamedFile fetchLogStream(String buildId)

    BuildLog fetchLogString(String buildId)
}
