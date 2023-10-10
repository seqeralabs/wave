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

package io.seqera.wave

import java.nio.file.Files
import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.runtime.Micronaut
import io.seqera.wave.util.BuildInfo
import io.seqera.wave.util.RuntimeInfo
/**
 * Registry app launcher
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Slf4j
class Application {

    static void main(String[] args) {
        log.info( "Starting ${BuildInfo.name} - version: ${BuildInfo.fullVersion} - ${RuntimeInfo.info('; ')} - CPUs ${Runtime.runtime.availableProcessors()}" )
        setupConfig()
        Micronaut.build(args)
                .banner(false)
                .eagerInitSingletons(true)
                .mainClass(Application.class)
                .start();
    }

    static void setupConfig() {
        // config file
        def configFile = Path.of('config.yml').toAbsolutePath()
        if( System.getenv('WAVE_CONFIG_FILE') ) {
            configFile = Path.of(System.getenv('WAVE_CONFIG_FILE')).toAbsolutePath()
            log.info "Detected WAVE_CONFIG_FILE variable: ${configFile}"
        }
        else {
            log.info "Default config file: ${configFile}"
        }
        if( !Files.exists(configFile) )
            throw new IllegalArgumentException("Config file does not exist or cannot be accessed: $configFile")
        System.setProperty('micronaut.config.files', "classpath:application.yml,file:$configFile")
    }
}
