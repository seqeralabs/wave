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

package io.seqera.wave.service.aws

import spock.lang.Requires
import spock.lang.Specification

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class AwsEcrServiceTest extends Specification {

    @Inject
    AwsEcrService provider

    @Requires({System.getenv('AWS_ACCESS_KEY_ID') && System.getenv('AWS_SECRET_ACCESS_KEY')})
    def 'should get registry token' () {
        given:
        def accessKey = System.getenv('AWS_ACCESS_KEY_ID')
        def secretKey = System.getenv('AWS_SECRET_ACCESS_KEY')
        def REGION = 'eu-west-1'

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

    @Requires({System.getenv('AWS_ACCESS_KEY_ID') && System.getenv('AWS_SECRET_ACCESS_KEY')})
    def 'should check registry info' () {
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

    def 'should detect role ARN pattern' () {
        expect:
        // Valid role ARNs
        AwsEcrService.isRoleArn('arn:aws:iam::123456789012:role/MyRole')
        AwsEcrService.isRoleArn('arn:aws:iam::123456789012:role/path/to/MyRole')
        AwsEcrService.isRoleArn('arn:aws:iam::999999999999:role/WaveEcrAccess')

        and:
        // Invalid patterns - access keys
        !AwsEcrService.isRoleArn('AKIAIOSFODNN7EXAMPLE')
        !AwsEcrService.isRoleArn('ASIATESTACCESSKEY')

        and:
        // Invalid patterns - malformed ARNs
        !AwsEcrService.isRoleArn('arn:aws:iam::123:role/MyRole') // too short account ID
        !AwsEcrService.isRoleArn('arn:aws:iam::12345678901234:role/MyRole') // too long account ID
        !AwsEcrService.isRoleArn('arn:aws:iam::123456789012:user/MyUser') // user not role
        !AwsEcrService.isRoleArn('arn:aws:s3:::my-bucket') // S3 ARN

        and:
        // Null and empty
        !AwsEcrService.isRoleArn(null)
        !AwsEcrService.isRoleArn('')
    }

    def 'should route to correct authentication method based on credential type' () {
        given:
        def service = new AwsEcrService()
        def region = 'us-east-1'

        expect: 'static credentials should not match role ARN pattern'
        def accessKey = 'AKIAIOSFODNN7EXAMPLE'
        !service.isRoleArn(accessKey)

        and: 'role ARN should match role ARN pattern'
        def roleArn = 'arn:aws:iam::123456789012:role/WaveEcrAccess'
        service.isRoleArn(roleArn)
    }

    def 'should create static credentials cache key correctly' () {
        given:
        def service = new AwsEcrService()
        def accessKey = 'AKIAIOSFODNN7EXAMPLE'
        def secretKey = 'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY'
        def region = 'us-east-1'

        when: 'creating cache key with static credentials'
        def credsClass = service.class.getDeclaredClasses().find { it.simpleName == 'AwsCreds' }
        def creds = credsClass.newInstance(accessKey, secretKey, null, region, false)

        then: 'credentials should be set correctly'
        creds.accessKey == accessKey
        creds.secretKey == secretKey
        creds.sessionToken == null
        creds.region == region
        creds.ecrPublic == false
    }

    def 'should create role-based credentials cache key correctly' () {
        given:
        def service = new AwsEcrService()
        def accessKeyId = 'ASIATEMPACCESSKEY'
        def secretAccessKey = 'tempSecretKey'
        def sessionToken = 'tempSessionToken'
        def region = 'us-east-1'

        when: 'creating cache key with session credentials'
        def credsClass = service.class.getDeclaredClasses().find { it.simpleName == 'AwsCreds' }
        def creds = credsClass.newInstance(accessKeyId, secretAccessKey, sessionToken, region, false)

        then: 'credentials should be set correctly'
        creds.accessKey == accessKeyId
        creds.secretKey == secretAccessKey
        creds.sessionToken == sessionToken
        creds.region == region
        creds.ecrPublic == false
    }

    def 'should include session token in cache key' () {
        given:
        def service = new AwsEcrService()

        when: 'creating cache key with session token'
        def creds1 = service.class.getDeclaredClasses()
                .find { it.simpleName == 'AwsCreds' }
                .newInstance('accessKey', 'secretKey', 'sessionToken1', 'us-east-1', false)
        def hash1 = creds1.stableHash()

        and: 'creating cache key with different session token'
        def creds2 = service.class.getDeclaredClasses()
                .find { it.simpleName == 'AwsCreds' }
                .newInstance('accessKey', 'secretKey', 'sessionToken2', 'us-east-1', false)
        def hash2 = creds2.stableHash()

        and: 'creating cache key without session token'
        def creds3 = service.class.getDeclaredClasses()
                .find { it.simpleName == 'AwsCreds' }
                .newInstance('accessKey', 'secretKey', null, 'us-east-1', false)
        def hash3 = creds3.stableHash()

        then: 'hashes should be different when session tokens differ'
        hash1 != hash2
        hash1 != hash3
        hash2 != hash3
    }

    def 'should require accessKey and region' () {
        given:
        def service = new AwsEcrService()

        when: 'missing accessKey'
        service.getLoginToken(null, 'secretKey', 'us-east-1', false)

        then:
        thrown(AssertionError)

        when: 'missing region'
        service.getLoginToken('AKIAIOSFODNN7EXAMPLE', 'secretKey', null, false)

        then:
        thrown(AssertionError)

        when: 'missing secretKey for static credentials'
        service.getLoginToken('AKIAIOSFODNN7EXAMPLE', null, 'us-east-1', false)

        then:
        thrown(AssertionError)
    }
}
