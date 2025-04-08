/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2025, Seqera Labs
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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.service.builder.BuildRequest

/**
 * Singularity helper methods
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@CompileStatic
class SingularityHelper {
    static class SingularityTypeAndBaseImage {
        String pullContainerFile
        String buildContainerFile
    }

    static SingularityTypeAndBaseImage modifyContainerFileForLocalImage(BuildRequest req) throws IllegalArgumentException{
        def pullLines = []
        def buildLines = []
        req.containerFile.eachLine { line ->
            if (line.trim().startsWith('Bootstrap:')) {
                buildLines << "Bootstrap: localimage"
                pullLines << line
            } else if (line.trim().startsWith('From:')) {
                buildLines << "From: $req.workDir/base_image.sif"
                pullLines << line
            } else {
                buildLines << line
            }
        }


        if (!pullLines) {
            throw new IllegalArgumentException("Container file does not contain 'Bootstrap:' or 'From:' lines for buildId: ${req.buildId}")
        }

        return new SingularityTypeAndBaseImage(
                pullContainerFile: pullLines.join('\n'),
                buildContainerFile: buildLines.join('\n')
        )
    }

}
