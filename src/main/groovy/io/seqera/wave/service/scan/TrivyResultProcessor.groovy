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

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import io.seqera.wave.exception.ScanRuntimeException
/**
 * Implements ScanStrategy for Docker
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
class TrivyResultProcessor {

    static List<ScanVulnerability> process(String trivyResult){
        final slurper = new JsonSlurper()
        try{
            final jsonMap = slurper.parseText(trivyResult) as Map
            return jsonMap.Results.collect { result ->
                result.Vulnerabilities.collect { vulnerability ->
                    new ScanVulnerability(
                            vulnerability.VulnerabilityID ?: "",
                            vulnerability.Severity ?: "",
                            vulnerability.Title ?: "",
                            vulnerability.PkgName ?: "",
                            vulnerability.InstalledVersion ?: "",
                            vulnerability.FixedVersion ?: "",
                            vulnerability.PrimaryURL ?: "")
                }
            }.flatten()
        }
        catch(Throwable e){
            throw new ScanRuntimeException("Failed to parse the trivy result", e)
        }
    }
}
