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

package io.seqera.wave.service.logs

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.local.LocalStorageConfiguration
import io.micronaut.objectstorage.local.LocalStorageOperations
import io.seqera.wave.configuration.LogsConfig
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
/**
 * Factory implementation for local ObjectStorageOperations
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Factory
@CompileStatic
@Slf4j
@Requires(property = 'wave.build.logs.local.path')
class LocalStorageOperationsFactory {

    @Inject
    private LogsConfig logsConfig

    @Singleton
    @Named("build-logs")
    ObjectStorageOperations<?, ?, ?> create() {
        final configuration = new LocalStorageConfiguration('build-logs')
        configuration.setEnabled(true)
        configuration.setPath(logsConfig.localPath)
        return new LocalStorageOperations(configuration)
    }
}
