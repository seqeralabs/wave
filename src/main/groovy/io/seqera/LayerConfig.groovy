package io.seqera

/**
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
