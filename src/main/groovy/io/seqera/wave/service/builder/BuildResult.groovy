package io.seqera.wave.service.builder

import groovy.transform.Canonical
import groovy.transform.CompileStatic

/**
 * Model a container builder request
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
