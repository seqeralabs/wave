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

import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
/**
 * Define a repository interface for {@link ScanRow} objects
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
interface ScanRepository extends CrudRepository<ScanRow, String> {

    /**
     * Find all scan records for the given scanId or a partial ID.
     *
     * @param scanId
     *      The scan ID e.g. {@code sc-12345_0} or partial ID without the
     *      incremental suffix {@code sc-12345}.
     * @return
     *      A list of {@link ScanRow} for the given scanId or {@code null}  if not matches can be found.
     */
    @Query('''
        SELECT s.* 
        FROM wave_scan s
        WHERE (data->>'id') ~ ('^(sc-)?' || :scanId || '_[0-9]+')
        ORDER BY (data->>'startTime')::timestamp DESC
        ''')
    List<ScanRow> findAllByScanId(String scanId)

}
