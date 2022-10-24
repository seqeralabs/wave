package io.seqera.wave.ratelimit

import groovy.transform.Canonical
import groovy.transform.CompileStatic


/**
 * A simple bean to contain the userId and Ip of a request
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Canonical
@CompileStatic
class AcquireRequest {

    /**
     * Principal key to use in the search. Can be null
     */
    String userId

    /**
     * Secondary key to use if principal is not present
     */
    String ip

}
