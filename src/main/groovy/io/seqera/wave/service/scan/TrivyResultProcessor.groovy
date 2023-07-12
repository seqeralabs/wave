package io.seqera.wave.service.scan

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

/**
 * Implements ScanStrategy for Docker
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
class TrivyResultProcessor {
    static List<Map<String,Object>> process(String trivyResult){
        JsonSlurper slurper = new JsonSlurper()
        List<Map<String, Object>> vulnerabilities
        try{
            Map<String, Object> jsonMap = slurper.parseText(trivyResult)
            vulnerabilities = jsonMap.Results.collect { result ->
                result.Vulnerabilities.collect { vulnerability ->
                    [
                            vulnerabilityId: vulnerability.VulnerabilityID ?: "",
                            severity: vulnerability.Severity ?: "",
                            title: vulnerability.Title ?: "",
                            pkgName: vulnerability.PkgName ?: "",
                            installedVersion: vulnerability.InstalledVersion ?: "",
                            fixedVersion: vulnerability.FixedVersion ?: "",
                            primaryUrl: vulnerability.PrimaryURL ?: "",
                    ]
                }
            }.flatten()
        }catch(Exception e){
            log.warn("Failed to parse the trivy result reason: ${e.getMessage()}", e)
        }

        return vulnerabilities
    }

    static List<Map<String,Object>> processLog(String trivyResult){
        return process(trivyResult.substring(trivyResult.indexOf("{"),trivyResult.lastIndexOf("}")+1))
    }
}
