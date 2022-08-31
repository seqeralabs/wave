package io.seqera.wave.api


import groovy.transform.Canonical
import groovy.transform.CompileStatic
/**
 * Model a container configuration
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class ContainerConfig {

    List<String> entrypoint
    List<String> cmd
    List<String> env
    String workingDir

    List<ContainerLayer> layers = []

    /**
     * Implements groovy truth for container config 
     */
    boolean asBoolean() {
        return entrypoint || cmd || env || workingDir || workingDir || layers
    }

    void validate(){
        for( ContainerLayer it : layers ) {
            it.validate()
        }
    }
}
