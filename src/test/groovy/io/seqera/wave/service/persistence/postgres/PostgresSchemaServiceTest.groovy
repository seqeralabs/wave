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
@Property(name = "datasources.default.url", value = "jdbc:tc:postgresql:///db")
class PostgresSchemaServiceTest extends Specification {

    @Inject
    JdbcOperations jdbcOperations

    @Inject
    PostgresSchemaService dbInitService


    def "should create tables successfully"() {
        when:
        dbInitService.create()

        then:
        def tables = jdbcOperations.prepareStatement(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'"
        ) { stmt ->
            def resultSet = stmt.executeQuery()
            def tableNames = []
            while (resultSet.next()) {
                tableNames << resultSet.getString("tablename")
            }
            return tableNames
        }

        expect: "the tables exist"
        tables.containsAll(["wave_build", "wave_request", "wave_scan", "wave_mirror"])
    }

}
