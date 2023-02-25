package io.seqera.wave.service.validation

import groovy.transform.CompileStatic
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.util.StringUtils
import jakarta.inject.Singleton
/**
 * Validation service
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class ValidationServiceImpl implements ValidationService {

    static private final List<String> VALID_PROTOCOLS = ['http','https']

    @Override
    String checkEndpoint(String endpoint) {
        final scheme = StringUtils.getUrlProtocol(endpoint)
        if( !scheme ) {
            return "Missing endpoint protocol — offending value: $endpoint"
        }

        if( scheme.toLowerCase() !in VALID_PROTOCOLS ) {
            return "Invalid endpoint protocol — offending value: $endpoint"
        }

        try {
            new URI(endpoint)
        }
        catch (URISyntaxException e) {
            return "Invalid endpoint '${endpoint}' — cause: ${e.message}"
        }

        return null
    }

    @Override
    String checkContainerName(String name) {
        // check does not start with a protocol prefix
        final prot = StringUtils.getUrlProtocol(name)
        if( prot ) {
            return "Invalid container repository name — offending value: $name"
        }

        try {
            ContainerCoordinates.parse(name)
        }
        catch (IllegalArgumentException e) {
            return "Invalid container image name — offending value: $name"
        }
        return null
    }

}
