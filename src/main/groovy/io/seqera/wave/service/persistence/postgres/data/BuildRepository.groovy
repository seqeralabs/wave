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

/**
 * Define a repository interface for {@link BuildRow} objects
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
interface BuildRepository extends CrudRepository<BuildRow, String> {

    /**
     * Find a build entry for the given target image name and digest
     *
     * @param targetImage
     *          The container image name for the build to be found
     * @param digest
     *          The container image digest for the build to be found.
     * @return
     *          The {@link BuildRow} entry matching the given parameters or {@code null}
     *          if not match can be found.
     */
    @Nullable
    @Query('''
        SELECT b.* 
        FROM wave_build b
        WHERE 
            data->>'targetImage' = :targetImage
            AND data->>'digest' = :digest
            AND (data->>'exitStatus')::int = 0
            AND data->>'duration' IS NOT NULL
        ORDER BY 
            (data->>'startTime')::timestamp DESC
        LIMIT 1
        ''')
    BuildRow findByTargetAndDigest(String targetImage, String digest)

    /**
     * Find a build entry by the build id or partial id.
     *
     * @param buildId
     *      The id of the build to be retrieved or a partial id string
     * @return
     *      The {@link BuildRow} matching the specified id or {@code null} if no matching
     *      record can be found.
     */
    @Nullable
    @Query('''
        SELECT w.* 
        FROM wave_build w 
        WHERE data->>'buildId' ~ :buildId
        ORDER BY (data->>'startTime')::timestamp DESC 
        LIMIT 1    
        ''')
    BuildRow findByBuildId(String buildId)

    /**
     * Find all builds records for the given build ID or a partial ID.
     *
     * @param buildId
     *      The build ID e.g. {@code bd-12345_0} or partial ID without the
     *      incremental suffix {@code bd-12345}.
     * @return
     *      A list of {@link BuildRow} for the given build ID or {@code null}  if not matches can be found.
     */
    @Query('''
        SELECT w.* 
        FROM wave_build w
        WHERE (data->>'buildId') ~ ('^(bd-)?' || :buildId || '_[0-9]+')
        ORDER BY (data->>'startTime')::timestamp DESC
        ''')
    List<BuildRow> findAllByBuildId(String buildId)
}
