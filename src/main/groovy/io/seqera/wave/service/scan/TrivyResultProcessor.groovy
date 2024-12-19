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

    /**
     * Parse a Trivy vulnerabilities JSON file and return a list of {@link ScanVulnerability} entries
     *
     * @param scanFile
     *      The {@link Path} of the Trivy JSON file to be scanned
     * @param maxEntries
     *      The max number of vulnerabilities that should be returned giving precedence to the
     *      most severe vulnerabilities e.g. one critical and one medium issues are found and
     *      1 is specified as {@code maxEntries} only the critical issues is returned.
     * @return
     *      The list of {@link ScanVulnerability} entries as parsed in from the JSON file.
     */
    static List<ScanVulnerability> parseFile(Path scanFile, Integer maxEntries=null) {
        final result = parseJson(scanFile.getText())
        return maxEntries>0 ? filter(result, maxEntries) : result
    }

    @CompileDynamic
    static List<ScanVulnerability> parseJson(String scanJson) {
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
