package io.seqera.wave.service.security

import io.seqera.wave.exchange.PairServiceResponse

/**
 * Provides public key generation for tower credentials integration.
 *
 * Once {@link SecurityService#getPublicKey(java.lang.String, java.lang.String)} is
 * called a new {@link KeyRecord} for the requested service is generated and cached until it expires.
 *
 * Further invocation of {@link SecurityService#getPublicKey(java.lang.String, java.lang.String)}
 * will not generate a new {@code KeyRecord} and return instead the public side of the already
 * generated one.
 *
 * Access to the currently generated {@code KeyRecord} for the corresponding service is provided
 * through {@link SecurityService#getServiceRegistration(java.lang.String, java.lang.String)}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface SecurityService {

    public static String TOWER_SERVICE = "tower"


    /**
     * Generates an return a key pair for the provided {@code service} available
     * at {@code endpoint}
     *
     * The key-pair is generated only if it is not already available for (service,endpoint)
     * otherwise the current key is returned.
     *
     * @param service The service name
     * @param endpoint The endpoint of the service
     * @return {@link PairServiceResponse} with the generated encoded public key
     */
    PairServiceResponse getPublicKey(String service, String endpoint)

    /**
     * Get the {@link KeyRecord} associated with {@code service} and {@code endpoint}
     * generated with {@link #getPublicKey(java.lang.String, java.lang.String)}
     *
     * @param service The service name
     * @param endpoint The endpoint of the service
     * @return {@link KeyRecord} if it has been generated and not expired, {@code null} otherwise
     */
    KeyRecord getServiceRegistration(String service, String endpoint)
    
}
