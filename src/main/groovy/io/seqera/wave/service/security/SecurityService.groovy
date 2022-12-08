package io.seqera.wave.service.security

import io.seqera.wave.exchange.RegisterInstanceResponse

/**
 * Provides public key generation for tower credentials integration
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface SecurityService {

    public static String TOWER_SERVICE = "tower"

    RegisterInstanceResponse getPublicKey(String service, String instanceId, String hostName)

    KeyRecord getServiceRegistration(String service, String instanceId)
}
