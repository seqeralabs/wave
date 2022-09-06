package io.seqera.wave.exception


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
class RateLimitException extends Exception{

    RateLimitException(String msg){
        super(msg)
    }
}
