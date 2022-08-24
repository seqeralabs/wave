package io.seqera.wave.api

import java.nio.file.Path

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

    ContainerConfig withBase(Path path){
        layers?.each {layer->
            layer.withBase(path)
        }
        this
    }

    void validate(){
        layers?.each{layer ->
            layer.validate()
        }
    }
}
