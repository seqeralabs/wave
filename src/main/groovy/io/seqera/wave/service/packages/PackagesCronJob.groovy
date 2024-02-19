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
package io.seqera.wave.service.packages


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Cron job to fetch packages
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */

@CompileStatic
@Singleton
@Slf4j
class PackagesCronJob {

    @Inject
    PackagesService service

    @ExecuteOn(TaskExecutors.SCHEDULED)
    @Scheduled(initialDelay = '${wave.package.cron.delay:3m}' , fixedDelay = '${wave.package.cron.interval:3h}')
    void fetch(){
        service.fetchCondaPackages()
    }
}
