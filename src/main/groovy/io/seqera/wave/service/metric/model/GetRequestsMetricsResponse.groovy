package io.seqera.wave.service.metric.model

import groovy.transform.CompileStatic;
/**
 * Model a Wave requests count per metric response
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
public class GetRequestsMetricsResponse {
    Map result

    GetRequestsMetricsResponse(Map result) {
        this.result = result
    }
}
