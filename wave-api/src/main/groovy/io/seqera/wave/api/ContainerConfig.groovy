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

    /**
     * Copy method
     *
     * @param that The {@link ContainerConfig} to be copied from
     */
    static ContainerConfig copy(ContainerConfig that, boolean stripData=false) {
        if( that==null )
            return null
        new ContainerConfig(
                that.entrypoint!=null ? new ArrayList<>(that.entrypoint) : null,
                that.cmd!=null ? new ArrayList<>(that.cmd) : null,
                that.env!=null ? new ArrayList<>(that.env) : null,
                that.workingDir,
                that.layers!=null ? that.layers.collect((it) -> ContainerLayer.copy(it, stripData)) : null
        )
    }
}
