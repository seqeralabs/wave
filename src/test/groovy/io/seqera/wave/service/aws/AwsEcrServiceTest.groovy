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

import java.time.Duration
import java.time.Instant
import java.util.function.Function

import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Unroll

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.aws.cache.AwsEcrAuthToken
import io.seqera.wave.service.aws.cache.AwsEcrCache
import jakarta.inject.Inject
import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.http.SdkHttpResponse
import software.amazon.awssdk.services.sts.model.Credentials
import software.amazon.awssdk.services.sts.model.StsException
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

    // --- computeCacheTtl tests ---

    def 'should compute TTL with 5-minute buffer for 1-hour expiration' () {
        given:
        def expiration = Instant.now().plus(Duration.ofHours(1))
        def maxDuration = Duration.ofHours(3)

        when:
        def ttl = AwsEcrService.computeCacheTtl(expiration, maxDuration)

        then:
        // 1h - 5min = 55min, should be ~55 minutes (allow 1 min tolerance for test execution time)
        ttl.toMinutes() >= 54
        ttl.toMinutes() <= 55
    }

    def 'should use maxDuration when expiration is far in the future' () {
        given:
        def expiration = Instant.now().plus(Duration.ofHours(10))
        def maxDuration = Duration.ofHours(3)

        when:
        def ttl = AwsEcrService.computeCacheTtl(expiration, maxDuration)

        then:
        ttl == maxDuration
    }

    def 'should return MIN_CACHE_TTL when credentials are nearly expired' () {
        given:
        def expiration = Instant.now().plus(Duration.ofMinutes(3)) // less than 5-min buffer
        def maxDuration = Duration.ofHours(3)

        when:
        def ttl = AwsEcrService.computeCacheTtl(expiration, maxDuration)

        then:
        ttl == Duration.ofMinutes(1)
    }

    def 'should return MIN_CACHE_TTL when credentials are already expired' () {
        given:
        def expiration = Instant.now().minus(Duration.ofMinutes(1))
        def maxDuration = Duration.ofHours(3)

        when:
        def ttl = AwsEcrService.computeCacheTtl(expiration, maxDuration)

        then:
        ttl == Duration.ofMinutes(1)
    }

    def 'should return maxDuration when expiration is null' () {
        given:
        def maxDuration = Duration.ofHours(3)

        when:
        def ttl = AwsEcrService.computeCacheTtl(null, maxDuration)

        then:
        ttl == maxDuration
    }

    @Unroll
    def 'should compute correct TTL for expiration in #minutes minutes' () {
        given:
        def expiration = Instant.now().plus(Duration.ofMinutes(minutes))
        def maxDuration = Duration.ofHours(3)

        when:
        def ttl = AwsEcrService.computeCacheTtl(expiration, maxDuration)

        then:
        ttl.toMinutes() >= expectedMinMinutes
        ttl.toMinutes() <= expectedMaxMinutes

        where:
        minutes | expectedMinMinutes | expectedMaxMinutes
        6       | 1                  | 1       // 6 - 5 = 1 min (at MIN_CACHE_TTL boundary)
        10      | 4                  | 5       // 10 - 5 = 5 min
        30      | 24                 | 25      // 30 - 5 = 25 min
        60      | 54                 | 55      // 60 - 5 = 55 min
        200     | 180                | 180     // 200 - 5 = 195 min, capped by maxDuration (180 min)
    }

    // --- End-to-end role assumption flow tests ---

    def 'should get login token with role assumption using mocked STS' () {
        given:
        def cache = Mock(AwsEcrCache)
        def service = Spy(AwsEcrService)
        service.@cache = cache

        def roleArn = 'arn:aws:iam::123456789012:role/WaveEcrAccess'
        def externalId = 'ext-id-123'
        def region = 'us-east-1'

        def stsCreds = Credentials.builder()
                .accessKeyId('ASIATEMPKEY')
                .secretAccessKey('tempSecret')
                .sessionToken('tempToken')
                .expiration(Instant.now().plus(Duration.ofHours(1)))
                .build()

        when:
        def result = service.getLoginTokenWithRole(roleArn, externalId, region, false)

        then:
        // Stub cache to invoke the loader lambda and return its result
        1 * cache.getOrCompute(_, _) >> { args ->
            def loader = args[1] as Function
            def pair = loader.apply('mock-key')
            return pair.first
        }
        1 * service.assumeRole(roleArn, externalId, region) >> stsCreds
        1 * cache.getDuration() >> Duration.ofHours(3)
        1 * service.load(_) >> new AwsEcrAuthToken('AWS:token123')

        and:
        result == 'AWS:token123'
    }

    def 'should get login token with role assumption for ECR public' () {
        given:
        def cache = Mock(AwsEcrCache)
        def service = Spy(AwsEcrService)
        service.@cache = cache

        def roleArn = 'arn:aws:iam::123456789012:role/WaveEcrAccess'
        def region = 'us-east-1'

        def stsCreds = Credentials.builder()
                .accessKeyId('ASIATEMPKEY')
                .secretAccessKey('tempSecret')
                .sessionToken('tempToken')
                .expiration(Instant.now().plus(Duration.ofHours(1)))
                .build()

        when:
        def result = service.getLoginTokenWithRole(roleArn, null, region, true)

        then:
        // Stub cache to invoke the loader lambda and return its result
        1 * cache.getOrCompute(_, _) >> { args ->
            def loader = args[1] as Function
            def pair = loader.apply('mock-key')
            return pair.first
        }
        1 * service.assumeRole(roleArn, null, region) >> stsCreds
        1 * cache.getDuration() >> Duration.ofHours(3)
        1 * service.load(_) >> new AwsEcrAuthToken('AWS:publictoken')

        and:
        result == 'AWS:publictoken'
    }

    def 'should route role ARN to getLoginTokenWithRole' () {
        given:
        def service = Spy(AwsEcrService)

        def roleArn = 'arn:aws:iam::123456789012:role/WaveEcrAccess'
        def externalId = 'ext-id-123'
        def region = 'us-east-1'

        when:
        service.getLoginToken(roleArn, externalId, region, false)

        then:
        1 * service.getLoginTokenWithRole(roleArn, externalId, region, false) >> 'AWS:token'
        0 * service.getLoginTokenWithStaticCredentials(_, _, _, _)
    }

    def 'should route static credentials to getLoginTokenWithStaticCredentials' () {
        given:
        def service = Spy(AwsEcrService)

        def accessKey = 'AKIAIOSFODNN7EXAMPLE'
        def secretKey = 'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY'
        def region = 'us-east-1'

        when:
        service.getLoginToken(accessKey, secretKey, region, false)

        then:
        0 * service.getLoginTokenWithRole(_, _, _, _)
        1 * service.getLoginTokenWithStaticCredentials(accessKey, secretKey, region, false) >> 'AWS:token'
    }

    // --- Error handling tests ---

    def 'should map AccessDenied STS exception' () {
        given:
        def stsEx = (StsException) StsException.builder()
                .message('Access Denied')
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode('AccessDenied')
                        .errorMessage('Access Denied')
                        .sdkHttpResponse(SdkHttpResponse.builder().statusCode(403).build())
                        .build())
                .statusCode(403)
                .build()

        when:
        def result = AwsEcrService.mapStsException(stsEx)

        then:
        result instanceof AwsEcrAuthException
        result.message.contains("Wave's service role does not have permission")
        result.message.contains('trust policy')
    }

    def 'should map RegionDisabledException STS exception' () {
        given:
        def stsEx = (StsException) StsException.builder()
                .message('Region disabled')
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode('RegionDisabledException')
                        .errorMessage('Region disabled')
                        .sdkHttpResponse(SdkHttpResponse.builder().statusCode(403).build())
                        .build())
                .statusCode(403)
                .build()

        when:
        def result = AwsEcrService.mapStsException(stsEx)

        then:
        result instanceof AwsEcrAuthException
        result.message.contains('STS is not enabled')
    }

    def 'should map unknown STS exception to generic message' () {
        given:
        def stsEx = (StsException) StsException.builder()
                .message('Something went wrong')
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode('UnknownError')
                        .errorMessage('Something went wrong')
                        .sdkHttpResponse(SdkHttpResponse.builder().statusCode(500).build())
                        .build())
                .statusCode(500)
                .build()

        when:
        def result = AwsEcrService.mapStsException(stsEx)

        then:
        result instanceof AwsEcrAuthException
        result.message.contains('STS AssumeRole failed')
    }

    // --- StsRetryPredicate tests ---

    def 'should retry on 5xx STS errors' () {
        given:
        def predicate = new StsRetryPredicate()
        def stsEx = (StsException) StsException.builder()
                .message('Service Unavailable')
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode('ServiceUnavailable')
                        .errorMessage('Service Unavailable')
                        .sdkHttpResponse(SdkHttpResponse.builder().statusCode(503).build())
                        .build())
                .statusCode(503)
                .build()

        expect:
        predicate.test(stsEx)
    }

    def 'should retry on 500 internal server error' () {
        given:
        def predicate = new StsRetryPredicate()
        def stsEx = (StsException) StsException.builder()
                .message('Internal Server Error')
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode('InternalError')
                        .errorMessage('Internal Server Error')
                        .sdkHttpResponse(SdkHttpResponse.builder().statusCode(500).build())
                        .build())
                .statusCode(500)
                .build()

        expect:
        predicate.test(stsEx)
    }

    def 'should not retry on 4xx STS client errors' () {
        given:
        def predicate = new StsRetryPredicate()
        def stsEx = (StsException) StsException.builder()
                .message('Access Denied')
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode('AccessDenied')
                        .errorMessage('Access Denied')
                        .sdkHttpResponse(SdkHttpResponse.builder().statusCode(403).build())
                        .build())
                .statusCode(403)
                .build()

        expect:
        !predicate.test(stsEx)
    }

    def 'should not retry on non-STS exceptions' () {
        given:
        def predicate = new StsRetryPredicate()

        expect:
        !predicate.test(new RuntimeException('something else'))
        !predicate.test(new IOException('network error'))
    }

    // --- Jump role chaining tests ---

    def 'should chain through jump role when configured' () {
        given:
        def service = Spy(AwsEcrService)
        service.@jumpRoleArn = 'arn:aws:iam::128997144437:role/test-jump-role'
        service.@jumpExternalId = 'ee79c296-cec7-4feb-9d0c-0c99f5a53cff'

        def roleArn = 'arn:aws:iam::123456789012:role/CustomerRole'
        def externalId = 'customer-ext-id'
        def region = 'us-east-1'

        def jumpCreds = Credentials.builder()
                .accessKeyId('ASIAJUMPTEMPKEY')
                .secretAccessKey('jumpTempSecret')
                .sessionToken('jumpTempToken')
                .expiration(Instant.now().plus(Duration.ofHours(1)))
                .build()

        when: 'assumeRole is called with jump role configured'
        try {
            service.assumeRole(roleArn, externalId, region)
        } catch (Exception ignored) {
            // Expected: STS call will fail with fake jump credentials
        }

        then: 'it should first assume the jump role'
        1 * service.assumeJumpRole(region) >> jumpCreds
    }

    def 'should not use jump role when not configured' () {
        given:
        def service = Spy(AwsEcrService)
        service.@jumpRoleArn = ''
        service.@jumpExternalId = ''

        def roleArn = 'arn:aws:iam::123456789012:role/WaveEcrAccess'
        def externalId = 'ext-id-123'
        def region = 'us-east-1'

        when: 'assumeRole is called without jump role configured'
        try {
            service.assumeRole(roleArn, externalId, region)
        } catch (Exception ignored) {
            // Expected: STS call will fail without real credentials
        }

        then: 'it should NOT call assumeJumpRole'
        0 * service.assumeJumpRole(_)
    }

    def 'should propagate jump role failure as AwsEcrAuthException' () {
        given:
        def cache = Mock(AwsEcrCache)
        def service = Spy(AwsEcrService)
        service.@cache = cache
        service.@jumpRoleArn = 'arn:aws:iam::128997144437:role/test-jump-role'
        service.@jumpExternalId = 'ee79c296-cec7-4feb-9d0c-0c99f5a53cff'

        def roleArn = 'arn:aws:iam::123456789012:role/CustomerRole'
        def region = 'us-east-1'

        def stsEx = (StsException) StsException.builder()
                .message('Access Denied')
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode('AccessDenied')
                        .errorMessage('Access Denied')
                        .sdkHttpResponse(SdkHttpResponse.builder().statusCode(403).build())
                        .build())
                .statusCode(403)
                .build()

        when:
        service.getLoginTokenWithRole(roleArn, null, region, false)

        then:
        1 * cache.getOrCompute(_, _) >> { args ->
            def loader = args[1] as Function
            loader.apply('mock-key')
        }
        1 * service.assumeRole(roleArn, null, region) >> { throw stsEx }

        and:
        def e = thrown(AwsEcrAuthException)
        e.message.contains("Wave's service role does not have permission")
    }

    def 'should map STS exception in getLoginTokenWithRole after retries exhausted' () {
        given:
        def cache = Mock(AwsEcrCache)
        def service = Spy(AwsEcrService)
        service.@cache = cache

        def roleArn = 'arn:aws:iam::123456789012:role/WaveEcrAccess'
        def region = 'us-east-1'

        def stsEx = (StsException) StsException.builder()
                .message('Access Denied')
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode('AccessDenied')
                        .errorMessage('Access Denied')
                        .sdkHttpResponse(SdkHttpResponse.builder().statusCode(403).build())
                        .build())
                .statusCode(403)
                .build()

        when:
        service.getLoginTokenWithRole(roleArn, null, region, false)

        then:
        1 * cache.getOrCompute(_, _) >> { args ->
            def loader = args[1] as Function
            loader.apply('mock-key')
        }
        1 * service.assumeRole(roleArn, null, region) >> { throw stsEx }

        and:
        def e = thrown(AwsEcrAuthException)
        e.message.contains("Wave's service role does not have permission")
    }
}
