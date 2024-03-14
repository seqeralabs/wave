package io.seqera.wave.service.metric.model

import groovy.transform.CompileStatic
/**
 * Model a Wave requests count response
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
class GetRequestsCountResponse {
    Long count

    GetRequestsCountResponse(Long count) {
        this.count = count
    }
}
