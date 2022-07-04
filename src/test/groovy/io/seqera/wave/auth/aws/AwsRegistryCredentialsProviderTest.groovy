package io.seqera.wave.auth.aws

import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class AwsRegistryCredentialsProviderTest extends Specification {

    def 'should get registry token' () {
        given:
        def accessKey = System.getenv('AWS_ACCESS_KEY_ID')
        def secretKey = System.getenv('AWS_SECRET_ACCESS_KEY')
        def provider = new AwsRegistryCredentialsProvider()

        when:
        def token = provider.getAwsCredentials(accessKey, secretKey, 'eu-west-1')
        then:
        token

    }

}
