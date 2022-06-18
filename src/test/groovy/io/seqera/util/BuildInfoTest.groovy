package io.seqera.util

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildIntoTest extends Specification {


    def 'should load version and commit id' () {
        expect:
        BuildInfo.getName() == 'wave'
        BuildInfo.getVersion()
        BuildInfo.getCommitId()
    }
}
