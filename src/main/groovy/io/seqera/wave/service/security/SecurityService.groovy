package io.seqera.wave.service.security

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface SecurityService {

    String getPublicKey(String service, String instanceId, String hostName)
}
