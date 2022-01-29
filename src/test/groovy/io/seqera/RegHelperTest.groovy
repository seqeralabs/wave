package io.seqera

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RegHelperTest extends Specification {

    def 'should compute digest' () {
        expect:
        RegHelper.digest(Mock.MANIFEST_LIST_CONTENT) == Mock.MANIFEST_DIGEST
    }

}
