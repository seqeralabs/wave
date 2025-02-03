package io.seqera.wave.service.metric.model

import groovy.transform.CompileStatic

@CompileStatic
class GetOrgArchCountResponse {
    String metric
    String arch
    Long count
    Map<String, Long> orgs

    GetOrgArchCountResponse(String metric, String arch, Long count, Map<String, Long> orgs) {
        this.metric = metric
        this.arch = arch
        this.count = count
        this.orgs = orgs
    }
}
