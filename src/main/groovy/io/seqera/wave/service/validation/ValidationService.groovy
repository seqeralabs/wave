package io.seqera.wave.service.validation

/**
 * Validation service
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface ValidationService {

    String checkEndpoint(String endpoint)

    String checkContainerName(String name)

}
