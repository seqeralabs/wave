package io.seqera.wave.service.metric.model

import groovy.transform.CompileStatic

@CompileStatic
class GetArchCountResponse {

    String arch
    String metric
    Long count

    GetArchCountResponse(String arch, String metric, Long count) {
        this.arch = arch
        this.metric = metric
        this.count = count
    }
}
