package io.seqera.wave.util

import spock.lang.Specification
import spock.lang.Unroll

class BucketTokenizerTest extends Specification {

    @Unroll
    def 'should parse bucket path #STR' () {
        expect:
        BucketTokenizer.from(STR) == EXPECTED
        where:
        STR             | EXPECTED
        '/'             | new BucketTokenizer(null, null, null)
        's3://foo'      | new BucketTokenizer('s3','foo','')
        's3://foo/'     | new BucketTokenizer('s3','foo','', true)
        's3://foo/x/y'  | new BucketTokenizer('s3','foo','/x/y')
        's3://foo/x/y/' | new BucketTokenizer('s3','foo','/x/y',true)
        and:
        'gs://foo/x/y'  | new BucketTokenizer('gs','foo','/x/y')
    }
}
