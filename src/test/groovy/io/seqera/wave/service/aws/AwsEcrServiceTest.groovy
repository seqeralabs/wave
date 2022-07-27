package io.seqera.wave.service.aws


import spock.lang.Requires
import spock.lang.Specification

import com.amazonaws.SdkClientException

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
        def creds = provider.getLoginToken(accessKey, secretKey, REGION).tokenize(":")
        then:
        creds[0] == 'AWS'
        creds[1].size() > 0

        when:
        provider.getLoginToken('a','b','c')
        then:
        thrown(SdkClientException)

    }

    def 'should check registry info' () {
        given:
        def provider = new AwsEcrService()
        expect:
        provider.getEcrHostInfo(null) == null
        provider.getEcrHostInfo('foo') == null
        provider.getEcrHostInfo('195996028523.dkr.ecr.eu-west-1.amazonaws.com') == new AwsEcrService.AwsEcrHostInfo('195996028523', 'eu-west-1')
        provider.getEcrHostInfo('195996028523.dkr.ecr.eu-west-1.amazonaws.com/foo') == new AwsEcrService.AwsEcrHostInfo('195996028523', 'eu-west-1')

    }

    def 'should check ecr registry' () {
        expect:
        !AwsEcrService.isEcrHost(null)
        !AwsEcrService.isEcrHost('foo')
        AwsEcrService.isEcrHost('195996028523.dkr.ecr.eu-west-1.amazonaws.com')
        AwsEcrService.isEcrHost('195996028523.dkr.ecr.eu-west-1.amazonaws.com/foo')

    }
}
