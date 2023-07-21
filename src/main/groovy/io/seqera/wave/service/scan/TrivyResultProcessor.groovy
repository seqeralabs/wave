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
