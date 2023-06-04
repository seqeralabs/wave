package io.seqera.wavelit.util

import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildInfoTest extends Specification {

    def 'should load version and commit id' () {
        expect:
        BuildInfo.getName() == 'wavelit'
        BuildInfo.getVersion()
        BuildInfo.getCommitId()
    }
}
