package io.seqera.wave.api


import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString

/**
 * Model a container configuration
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includePackage = false, includeNames = true)
@Canonical
@CompileStatic
class ContainerConfig {

    List<String> entrypoint
    List<String> cmd
    List<String> env
    String workingDir

    List<ContainerLayer> layers = new ArrayList<>(10)

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
