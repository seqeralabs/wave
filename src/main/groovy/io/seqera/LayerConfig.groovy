package io.seqera

import groovy.transform.CompileStatic

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class LayerConfig {

    List<String> entrypoint
    List<String> cmd
    List<String> env
    String workingDir
    LayerMeta append

}
