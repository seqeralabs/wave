package io.seqera.wave.service.build

import groovy.transform.Canonical
import groovy.transform.CompileStatic

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class BuildResult {

    private int exitStatus
    private String error

    BuildResult(int exitStatus, String error) {
        this.error = error
        this.exitStatus = exitStatus
    }

    int getExitStatus() { exitStatus }

    String getError() { error }
}
