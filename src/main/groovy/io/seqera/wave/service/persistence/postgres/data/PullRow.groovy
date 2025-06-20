/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2025, Seqera Labs
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

package io.seqera.wave.service.persistence.postgres.data

import java.time.Instant

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.Introspected
import io.micronaut.data.annotation.AutoPopulated
import io.micronaut.data.annotation.MappedEntity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Model a row entry in the {@code wave_pull} table
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MappedEntity
@Introspected
@CompileStatic
@Table(name = "wave_pull")
class PullRow {

    @Id
    @AutoPopulated
    UUID id

    String requestId

    Instant createdAt

}
