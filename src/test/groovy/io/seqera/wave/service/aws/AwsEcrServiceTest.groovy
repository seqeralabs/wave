/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

package io.seqera.wave.service.aws


import spock.lang.Requires
import spock.lang.Specification


/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class AwsEcrServiceTest extends Specification {

    @Requires({System.getenv('AWS_ACCESS_KEY_ID') && System.getenv('AWS_SECRET_ACCESS_KEY')})
    def 'should get registry token' () {
        given:
        def accessKey = System.getenv('AWS_ACCESS_KEY_ID')
        def secretKey = System.getenv('AWS_SECRET_ACCESS_KEY')
        def REGION = 'eu-west-1'
        def provider = new AwsEcrService()

        when:
        def creds = provider.getLoginToken(accessKey, secretKey, REGION, false).tokenize(":")
        then:
        creds[0] == 'AWS'
        creds[1].size() > 0

        when:
        provider.getLoginToken('a','b','c',false)
        then:
        thrown(Exception)
    }

    def 'should check registry info' () {
        given:
        def provider = new AwsEcrService()
        expect:
        provider.getEcrHostInfo(null) == null
        provider.getEcrHostInfo('foo') == null
        provider.getEcrHostInfo('195996028523.dkr.ecr.eu-west-1.amazonaws.com') == new AwsEcrService.AwsEcrHostInfo('195996028523', 'eu-west-1')
        provider.getEcrHostInfo('195996028523.dkr.ecr.eu-west-1.amazonaws.com/foo') == new AwsEcrService.AwsEcrHostInfo('195996028523', 'eu-west-1')
        and:
        provider.getEcrHostInfo('public.ecr.aws') == new AwsEcrService.AwsEcrHostInfo(null, 'us-east-1')

    }

    def 'should check ecr registry' () {
        expect:
        !AwsEcrService.isEcrHost(null)
        !AwsEcrService.isEcrHost('foo')
        AwsEcrService.isEcrHost('195996028523.dkr.ecr.eu-west-1.amazonaws.com')
        AwsEcrService.isEcrHost('195996028523.dkr.ecr.eu-west-1.amazonaws.com/foo')
        and:
        AwsEcrService.isEcrHost('public.ecr.aws')
        AwsEcrService.isEcrHost('public.ecr.aws/foo')
        !AwsEcrService.isEcrHost('public.ecr.com')
    }
}
