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

package io.seqera.wave.service.persistence.postgres

import java.sql.SQLException

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.data.jdbc.runtime.JdbcOperations
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Implements the PostgreSQL schema initialization
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(env='postgres')
@Singleton
@CompileStatic
class PostgresSchemaService {

    @Inject
    private JdbcOperations jdbcOperations

    final static private String ddl = '''
            CREATE TABLE IF NOT EXISTS wave_build (
                id TEXT PRIMARY KEY,
                data JSONB NOT NULL,
                created_at TIMESTAMP DEFAULT NOW()
            );

            CREATE TABLE IF NOT EXISTS wave_request (
                id TEXT PRIMARY KEY,
                data JSONB NOT NULL,
                created_at TIMESTAMP DEFAULT NOW()
            );

            CREATE TABLE IF NOT EXISTS wave_scan (
                id TEXT PRIMARY KEY,
                data JSONB NOT NULL,
                created_at TIMESTAMP DEFAULT NOW()
            );
            
            CREATE TABLE IF NOT EXISTS wave_mirror (
                id TEXT PRIMARY KEY,
                data JSONB NOT NULL,
                created_at TIMESTAMP DEFAULT NOW()
            );

            CREATE INDEX IF NOT EXISTS wave_request_data_gin_idx 
            ON wave_request USING GIN (data);

            CREATE INDEX IF NOT EXISTS wave_build_data_gin_idx 
            ON wave_build USING GIN (data);

            CREATE INDEX IF NOT EXISTS wave_scan_data_gin_idx
            ON wave_scan USING GIN (data);

            CREATE INDEX IF NOT EXISTS wave_mirror_data_gin_idx
            ON wave_mirror USING GIN (data);
            '''.stripIndent()


    void create() {
        jdbcOperations.execute((conn)-> {
            try(final stmt = conn.createStatement()) {
                stmt.execute(ddl)
            }
            catch (SQLException e) {
                throw new RuntimeException("Failed to initialize PostgreSQL tables", e)
            }
        })
    }
}
