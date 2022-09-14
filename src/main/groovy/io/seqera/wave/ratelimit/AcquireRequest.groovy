package io.seqera.wave.ratelimit


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
class AcquireRequest {

    /**
     * Principal key to use if present, for example the userId
     */
    String key

    /**
     * Secondary key to use if principal is not present, for example the ip of the request
     */
    String subKey

    AcquireRequest(String key, String subKey) {
        this.key = key
        this.subKey = subKey
    }
}
