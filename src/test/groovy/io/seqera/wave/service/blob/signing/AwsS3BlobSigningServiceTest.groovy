package io.seqera.wave.service.blob.signing

import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class AwsS3BlobSigningServiceTest extends Specification {

    def 'should unescape uri' () {
        given:
        def service = new AwsS3BlobSigningService()

        expect:
        service.unescapeUriPath(PATH) == EXPECTED

        where:
        PATH                        | EXPECTED
        null                        | null
        'http:/foo.com/bar'         | 'http:/foo.com/bar'
        'http:/foo.com/this?that'   | 'http:/foo.com/this?that'
        'http:/foo.com/x%3Ay%3Az'   | 'http:/foo.com/x:y:z'
        'http:/foo.com/?x%3Ay%3Az'  | 'http:/foo.com/?x%3Ay%3Az'
    }
}
