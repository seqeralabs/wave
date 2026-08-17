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
import java.util.regex.Pattern

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.data.jdbc.runtime.JdbcOperations
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Implements the PostgreSQL schema initialization
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(env='postgres')
@Context
@Singleton
@CompileStatic
class PostgresSchemaService {

    final static private Pattern SAFE_IDENT = Pattern.compile('[a-z_][a-z0-9_]*')

    @Inject
    private JdbcOperations jdbcOperations

    @Value('${wave.db.schema:wave}')
    private String schemaName

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

            '''.stripIndent()

    @PostConstruct
    void init() {
        create()
    }

    void create() {
        assertSafeIdentifier(schemaName)
        assertNoLegacyTablesInPublic()
        jdbcOperations.execute((conn)-> {
            try(final stmt = conn.createStatement()) {
                stmt.execute("CREATE SCHEMA IF NOT EXISTS ${schemaName} AUTHORIZATION CURRENT_USER")
                stmt.execute("SET search_path = ${schemaName}")
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

    private void assertNoLegacyTablesInPublic() {
        final count = jdbcOperations.prepareStatement(
                "SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tablename LIKE 'wave\\_%' ESCAPE '\\'"
        ) { stmt ->
            final resultSet = stmt.executeQuery()
            return resultSet.next() ? resultSet.getInt(1) : 0
        }
        if( count>0 ) {
            throw new IllegalStateException(
                    "${count} legacy wave_* table(s) in 'public' require migration to '${schemaName}' before starting this build. Contact support.")
        }
    }

    static private void assertSafeIdentifier(String name) {
        if( !name || !SAFE_IDENT.matcher(name).matches() ) {
            throw new IllegalStateException("Invalid wave.db.schema: must match ${SAFE_IDENT.pattern()} (got: ${name})")
        }
    }
}
