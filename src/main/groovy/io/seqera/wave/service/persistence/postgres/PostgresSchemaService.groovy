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
            -- BUILD entity 
            CREATE TABLE IF NOT EXISTS wave_build (
                id TEXT PRIMARY KEY,
                created_at TIMESTAMP DEFAULT NOW(),
                data JSONB NOT NULL
            );

            CREATE INDEX IF NOT EXISTS wave_build_data_gin_idx 
            ON wave_build USING GIN (data);

            CREATE INDEX IF NOT EXISTS wave_build_created_at_idx
            ON wave_build (created_at);

            -- REQUEST entity 
            CREATE TABLE IF NOT EXISTS wave_request (
                id TEXT PRIMARY KEY,
                created_at TIMESTAMP DEFAULT NOW(),
                data JSONB NOT NULL
            );

            CREATE INDEX IF NOT EXISTS wave_request_data_gin_idx 
            ON wave_request USING GIN (data);

            CREATE INDEX IF NOT EXISTS wave_request_created_at_idx
            ON wave_request (created_at);

            -- MIRROR entity 
            CREATE TABLE IF NOT EXISTS wave_mirror (
                id TEXT PRIMARY KEY,
                created_at TIMESTAMP DEFAULT NOW(),
                data JSONB NOT NULL
            );

            CREATE INDEX IF NOT EXISTS wave_mirror_data_gin_idx
            ON wave_mirror USING GIN (data);

            CREATE INDEX IF NOT EXISTS wave_mirror_created_at_idx
            ON wave_mirror (created_at);

            -- SCAN entity 
            CREATE TABLE IF NOT EXISTS wave_scan (
                id TEXT PRIMARY KEY,
                created_at TIMESTAMP DEFAULT NOW(),
                data JSONB NOT NULL
            );

            CREATE INDEX IF NOT EXISTS wave_scan_start_time_idx
            ON wave_scan ((data->>'startTime'));

            CREATE INDEX IF NOT EXISTS wave_scan_created_at_idx
            ON wave_scan (created_at);

            CREATE TABLE IF NOT EXISTS wave_pull (
                id BIGSERIAL PRIMARY KEY,
                request_id TEXT,
                created_at TIMESTAMP DEFAULT NOW(),
                CONSTRAINT fk_request
                FOREIGN KEY (request_id)
                REFERENCES wave_request(id)
                ON DELETE NO ACTION
            );

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

    void deleteAll() {
        final stm = '''\
        DELETE FROM wave_build;
        DELETE FROM wave_request;
        DELETE FROM wave_mirror;
        DELETE FROM wave_scan;
        DELETE FROM wave_pull;
        '''.stripIndent()

        jdbcOperations.execute((conn)-> {
            try(final stmt = conn.createStatement()) {
                stmt.execute(stm)
            }
            catch (SQLException e) {
                throw new RuntimeException("Failed to truncate PostgreSQL tables", e)
            }
        })
    }
}
