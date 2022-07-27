package io.seqera.wave.api

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.builder.Builder

/**
 * Model a container configuration
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Builder
@Canonical
@CompileStatic
class ContainerConfig {

    List<String> entrypoint
    List<String> cmd
    List<String> env
    String workingDir

    List<ContainerLayer> layers

    /**
     * Implements groovy truth for container config 
     */
    boolean asBoolean() {
        return entrypoint || cmd || env || workingDir || workingDir || layers
    }
}
