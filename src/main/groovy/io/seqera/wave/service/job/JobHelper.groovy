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

package io.seqera.wave.service.job

import java.nio.file.Files

import groovy.transform.CompileStatic
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import io.seqera.wave.configuration.BuildConfig
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import static io.seqera.wave.service.aws.ObjectStorageOperationsFactory.BUILD_WORKSPACE

/**
 * Helper class to handle jobs execution common logic
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class JobHelper {

    @Inject
    @Named(BUILD_WORKSPACE)
    private ObjectStorageOperations<?, ?, ?> objectStorageOperations

    @Inject
    BuildConfig buildConfig

    void saveDockerAuth(String id, String authJson) {
        // save docker config for creds
        if( authJson ) {
            objectStorageOperations.upload(UploadRequest.fromBytes(authJson.bytes, "$buildConfig.workspacePrefix/$id/config.json".toString()))
        }
    }

}
