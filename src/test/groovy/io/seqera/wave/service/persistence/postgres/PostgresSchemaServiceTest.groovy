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


import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest(environments = 'postgres', startApplication = false)
@Property(name = "datasources.default.driver-class-name", value = "org.testcontainers.jdbc.ContainerDatabaseDriver")
@Property(name = "datasources.default.url", value = "jdbc:tc:postgresql:16.14:///db")
class PostgresSchemaServiceTest extends Specification {

    private static final List<String> WAVE_TABLES = ["wave_build", "wave_request", "wave_scan", "wave_mirror"]

    @Inject
    JdbcOperations jdbcOperations

    @Inject
    PostgresSchemaService dbInitService


    def "should create tables successfully"() {
        when:
        dbInitService.create()

        then:
        def tables = tableNames(jdbcOperations, 'wave')
        def publicTables = tableNames(jdbcOperations, 'public')

        expect: "the tables exist"
        tables.containsAll(WAVE_TABLES)
        !publicTables.any { it.startsWith('wave_') }
    }

    def "should create tables in a custom schema"() {
        given:
        final schema = 'wave_custom'
        final ctx = ApplicationContext.run(postgresProperties("custom_${System.nanoTime()}", schema), 'postgres')

        when:
        final jdbc = ctx.getBean(JdbcOperations)

        then:
        tableNames(jdbc, schema).containsAll(WAVE_TABLES)
        !tableNames(jdbc, 'public').any { it.startsWith('wave_') }

        cleanup:
        ctx?.close()
    }

    def "should reject invalid schema names"() {
        when:
        final ctx = ApplicationContext.run(postgresProperties("invalid_${System.nanoTime()}", 'wave-bad'), 'postgres')
        ctx.close()

        then:
        final error = thrown(Throwable)
        rootMessages(error).any { it.contains('Invalid wave.db.schema') }
    }

    def "should fail when legacy wave tables remain in public"() {
        when:
        final props = postgresProperties("legacy_${System.nanoTime()}", 'wave') + [
                "datasources.default.url": "jdbc:tc:postgresql:15:///legacy_${System.nanoTime()}?TC_INITSCRIPT=postgres/public-wave-legacy.sql"
        ]
        final ctx = ApplicationContext.run(props, 'postgres')
        ctx.close()

        then:
        final error = thrown(Throwable)
        rootMessages(error).any { it.contains("legacy wave_* table(s) in 'public' require migration to 'wave'") }
    }

    private static Map<String, Object> postgresProperties(String dbName, String schema) {
        return [
                "datasources.default.driver-class-name": "org.testcontainers.jdbc.ContainerDatabaseDriver",
                "datasources.default.url": "jdbc:tc:postgresql:15:///${dbName}",
                "wave.db.schema": schema
        ]
    }

    private static List<String> tableNames(JdbcOperations jdbc, String schema) {
        return jdbc.prepareStatement(
                "SELECT tablename FROM pg_tables WHERE schemaname = ?"
        ) { stmt ->
            stmt.setString(1, schema)
            final resultSet = stmt.executeQuery()
            final tableNames = []
            while (resultSet.next()) {
                tableNames << resultSet.getString("tablename")
            }
            return tableNames
        } as List<String>
    }

    private static List<String> rootMessages(Throwable error) {
        final result = []
        def current = error
        while( current ) {
            if( current.message )
                result << current.message
            current = current.cause
        }
        return result as List<String>
    }
}
