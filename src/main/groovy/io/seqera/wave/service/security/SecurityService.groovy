package io.seqera.wave.service.security

import io.seqera.wave.exchange.PairServiceResponse

/**
 * Provides public key generation for tower credentials integration
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface SecurityService {

    public static String TOWER_SERVICE = "tower"

    PairServiceResponse getPublicKey(String service, String endpoint)

    KeyRecord getServiceRegistration(String service, String endpoint)
    
}
