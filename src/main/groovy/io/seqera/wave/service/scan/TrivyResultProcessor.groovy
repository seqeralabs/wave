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
