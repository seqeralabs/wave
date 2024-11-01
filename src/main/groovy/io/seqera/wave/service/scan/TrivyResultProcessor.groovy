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

import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.exception.ScanRuntimeException
/**
 * Implements ScanStrategy for Docker
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class TrivyResultProcessor {

    static List<ScanVulnerability> parse(Path scanFile) {
        return parse(scanFile.getText())
    }

    static List<ScanVulnerability> parse(Path scanFile, int maxEntries) {
        final result = parse(scanFile)
        return filter(result, maxEntries)
    }

    @CompileDynamic
    static List<ScanVulnerability> parse(String scanJson) {
        final slurper = new JsonSlurper()
        try{
            final jsonMap = slurper.parseText(scanJson) as Map
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

    static protected List<ScanVulnerability> filter(List<ScanVulnerability> vulnerabilities, int limit){
        vulnerabilities.toSorted((v,w) -> w.compareTo(v)).take(limit)
    }
}
