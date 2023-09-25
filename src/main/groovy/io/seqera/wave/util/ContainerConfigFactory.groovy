/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

package io.seqera.wave.util

import java.nio.file.Files
import java.nio.file.Path

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.api.ContainerConfig

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Singleton
@Slf4j
@CompileStatic
class ContainerConfigFactory {

    ContainerConfig from(Path path) {
        log.debug "ContainerConfig from path: $path"
        final layerConfigPath = path.toAbsolutePath()
        if( !Files.exists(layerConfigPath) ) {
            throw new IllegalArgumentException("Specific config path does not exist: $layerConfigPath")
        }
        return parse(path.text)
    }

    ContainerConfig from(String text) {
        log.trace "ContainerConfig from inputStream"
        return parse(text)
    }


    protected ContainerConfig parse(String text){
        final type = new TypeToken<ContainerConfig>(){}.getType()
        final ContainerConfig containerConfig = new Gson().fromJson(text, type)
        log.trace "Layer info: $containerConfig"
        return containerConfig
    }
}
