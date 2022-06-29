package io.seqera.wave.model

/**
 * Model the settings to be injected into the pulled container
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class LayerConfig {

    List<String> entrypoint
    List<String> cmd
    List<String> env
    String workingDir
    LayerMeta append

}
