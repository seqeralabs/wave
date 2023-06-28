package io.seqera.wave.service.persistence

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.util.logging.Slf4j

@Slf4j
@ToString(includeNames = true, includePackage = false)
@Canonical
@CompileStatic
class WaveContainerScanRecord {
    String buildId
    String scanResult
}
