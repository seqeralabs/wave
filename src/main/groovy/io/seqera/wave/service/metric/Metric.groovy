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

package io.seqera.wave.service.metric

/**
 * enum for Metrics
 * This enum is created to avoid duplicating the same code in MetricController, MetricService and PersistenceService classes for ip, user
 * buildLabel and pullLabel are the database column names of wave_build and wave_request respectively
 * This enum is used in MetricController, MetricService and PersistenceService to create generic methods for ip, user
 * These labels are used in GROUP BY clause of metric SELECT queries in SurrealPersistenceService class to get the results per ip, user
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
enum Metric {
    ip('requestIp', 'ipAddress'),
    user('userEmail', 'user.email')

    String buildLabel
    String pullLabel
    Metric(String buildLabel, pullLabel){
        this.buildLabel = buildLabel
        this.pullLabel = pullLabel
    }
}
