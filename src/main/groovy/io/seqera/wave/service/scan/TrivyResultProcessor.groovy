package io.seqera.wave.service.scan

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import io.seqera.wave.exception.ScanRuntimeException
import io.seqera.wave.model.ScanVulnerability

/**
 * Implements ScanStrategy for Docker
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
class TrivyResultProcessor {
    static List<ScanVulnerability> process(String trivyResult){
        JsonSlurper slurper = new JsonSlurper()
        List<ScanVulnerability> vulnerabilities
        try{
            Map<String, Object> jsonMap = slurper.parseText(trivyResult)
            vulnerabilities = jsonMap.Results.collect { result ->
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
        }catch(Exception e){
            throw new ScanRuntimeException("Failed to parse the trivy result", e)
        }

        return vulnerabilities
    }
}
