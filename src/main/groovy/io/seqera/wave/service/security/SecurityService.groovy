package io.seqera.wave.service.security

import io.seqera.wave.exchange.RegisterInstanceResponse

/**
 * Provides public key generation for tower credentials integration
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface SecurityService {

    RegisterInstanceResponse getPublicKey(String service, String instanceId, String hostName)
}
