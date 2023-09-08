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

package io.seqera.wave.service.scan

import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.configuration.ScanConfig
/**
 * Implements ScanStrategy for Docker
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@CompileStatic
abstract class ScanStrategy {

    abstract ScanResult scanContainer(ScanRequest request)

    protected List<String> scanCommand(String targetImage, Path outputFile, ScanConfig config) {
        def cmd = ['--quiet',
                'image',
                '--timeout',
                "${config.timeout.toMinutes()}m".toString(),
                '--format',
                'json',
                '--output',
                outputFile.toString()]

        if( config.severity ) {
            cmd << '--severity'
            cmd << config.severity
        }
        cmd << targetImage
        return cmd
    }
}
