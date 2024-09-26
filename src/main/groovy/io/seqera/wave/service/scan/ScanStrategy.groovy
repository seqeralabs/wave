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

package io.seqera.wave.service.scan

import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.ScanConfig
/**
 * Implements ScanMode for Docker
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@CompileStatic
@Requires(bean = ScanConfig)
abstract class ScanStrategy {

    abstract void scanContainer(String jobName, ScanRequest request)

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
