package io.seqera.wave.exception

import groovy.transform.CompileStatic


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@CompileStatic
class RateLimitException extends Exception{

    RateLimitException(String msg){
        super(msg)
    }
}
