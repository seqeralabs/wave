package io.seqera

import groovy.transform.CompileStatic
import io.micronaut.runtime.Micronaut

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class Application {
    static void main(String[] args) {
        Micronaut.build(args)
                .mainClass(Application.class)
                .start();
    }
}
