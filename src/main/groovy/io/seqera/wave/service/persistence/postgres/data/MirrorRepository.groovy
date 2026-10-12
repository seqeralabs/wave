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

package io.seqera.wave.service.persistence.postgres.data

import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.seqera.wave.service.mirror.MirrorResult

/**
 * Define a repository interface for {@link MirrorRow} objects
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
interface MirrorRepository extends CrudRepository<MirrorRow, String> {

    @Nullable
    @Query('''
        SELECT * 
        FROM wave_mirror
        WHERE 
            data->>'targetImage' = :targetImage
            AND data->>'digest' = :digest
            AND (data->>'exitCode')::int = 0
            AND data->>'status' = :status
        ORDER BY 
            (data->>'creationTime')::timestamp DESC
        LIMIT 1
        ''')
    MirrorRow findByTargetAndDigest(String targetImage, String digest, MirrorResult.Status status)
}
