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

package io.seqera.wave.service.retention

import groovy.transform.CompileStatic
/**
 * Remove the records and the artifacts they reference once their retention period has elapsed
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
interface RetentionService {

    /**
     * Remove every record, and the artifacts it references, older than the period configured
     * for its type. Safe to repeat: a partially completed purge is resumed by the next run.
     *
     * @return The number of records removed across all types
     */
    long purge()

}
