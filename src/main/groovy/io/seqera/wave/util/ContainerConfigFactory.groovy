/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
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
