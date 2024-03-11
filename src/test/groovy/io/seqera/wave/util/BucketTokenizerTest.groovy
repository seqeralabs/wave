/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
