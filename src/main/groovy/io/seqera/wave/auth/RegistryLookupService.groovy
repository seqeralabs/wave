package io.seqera.wave.auth

/**
 * Lookup service for container registry
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface RegistryLookupService {

    /**
     * Given a registry name lookup for the corresponding
     * auth endpoint
     *
     * @param registry
 *         The registry name e.g. {@code docker.io} or {@code quay.io}
     * @return The corresponding {@link RegistryAuth} object holding the realm URI and service info,
     *     or {@code null} if nothing is found
     */
    RegistryInfo lookup(String registry)
    URI registryEndpoint(String registry)
}
