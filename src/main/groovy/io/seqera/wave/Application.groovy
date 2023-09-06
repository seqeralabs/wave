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
