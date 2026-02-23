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
import java.util.concurrent.ExecutorService
import java.util.regex.Pattern

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.service.aws.cache.AwsEcrAuthToken
import io.seqera.wave.service.aws.cache.AwsEcrCache
import io.seqera.cache.tiered.AbstractTieredCache
import io.seqera.cache.tiered.TieredKey
import io.seqera.wave.util.RegHelper
import io.seqera.wave.util.StringUtils
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ecr.EcrClient
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenRequest
import software.amazon.awssdk.services.ecrpublic.EcrPublicClient
import software.amazon.awssdk.services.ecrpublic.model.GetAuthorizationTokenRequest as GetPublicAuthorizationTokenRequest
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest
import software.amazon.awssdk.services.sts.model.Credentials
import software.amazon.awssdk.services.sts.model.StsException
/**
 * Implement AWS ECR login service
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class AwsEcrService {

    static final private Pattern AWS_ECR_PRIVATE = ~/^(\d+)\.dkr\.ecr\.([a-z\-\d]+)\.amazonaws\.com/

    static final private Pattern AWS_ECR_PUBLIC = ~/public\.ecr\.aws/

    static final private Pattern AWS_ROLE_ARN = ~/^arn:aws:iam::\d{12}:role\/.+/

    /**
     * Buffer time before credential expiration to trigger refresh
     */
    static final private Duration REFRESH_BUFFER = Duration.ofMinutes(5)

    /**
     * Minimum cache TTL to avoid caching nearly-expired credentials
     */
    static final private Duration MIN_CACHE_TTL = Duration.ofMinutes(1)

    @Canonical
    private static class AwsCreds implements TieredKey {
        String accessKey
        String secretKey
        String sessionToken
        String region
        boolean ecrPublic

        @Override
        String stableHash() {
            final token = sessionToken ?: ''
            return RegHelper.sipHash(accessKey, secretKey, token, region, ecrPublic)
        }
    }

    @Canonical
    static class AwsEcrHostInfo {
        String account
        String region
    }

    AwsEcrAuthToken load(AwsCreds creds) throws Exception {
        def token = creds.ecrPublic
                ? getLoginToken1(creds.accessKey, creds.secretKey, creds.sessionToken, creds.region)
                : getLoginToken0(creds.accessKey, creds.secretKey, creds.sessionToken, creds.region)
        return new AwsEcrAuthToken(token)
    }

    @Inject
    @Named(TaskExecutors.BLOCKING)
    private ExecutorService ioExecutor

    @Inject
    private AwsEcrCache cache

    /**
     * Check if the provided access key is actually an AWS IAM role ARN
     *
     * @param accessKey The access key or role ARN
     * @return true if the accessKey matches the role ARN pattern
     */
    protected static boolean isRoleArn(String accessKey) {
        return accessKey?.matches(AWS_ROLE_ARN)
    }

    /**
     * Create an STS client using Wave's default credentials provider
     *
     * @param region AWS region
     * @return StsClient instance
     */
    protected static StsClient stsClient(String region) {
        return StsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build()
    }

    /**
     * Assume an IAM role and return temporary credentials
     *
     * @param roleArn The ARN of the role to assume
     * @param externalId The external ID for cross-account role assumption (optional)
     * @param region AWS region
     * @return Temporary AWS credentials with session token
     */
    protected Credentials assumeRole(String roleArn, String externalId, String region) {
        log.trace "Assuming AWS role: $roleArn; region: $region; externalId: ${externalId ? 'provided' : 'none'}"

        try {
            final client = stsClient(region)
            final requestBuilder = AssumeRoleRequest.builder()
                    .roleArn(roleArn)
                    .roleSessionName("wave-ecr-${System.currentTimeMillis()}")
                    .durationSeconds(3600) // 1 hour

            // Add external ID if provided
            if (externalId) {
                requestBuilder.externalId(externalId)
            }

            final request = requestBuilder.build()
            final response = client.assumeRole(request as AssumeRoleRequest)
            return response.credentials()
        }
        catch (StsException e) {
            throw mapStsException(e)
        }
    }

    /**
     * Map STS exceptions to more user-friendly error messages
     *
     * @param e The STS exception
     * @return A Wave exception with helpful error message
     */
    protected static Exception mapStsException(StsException e) {
        final code = e.awsErrorDetails()?.errorCode()

        switch (code) {
            case 'AccessDenied':
                return new AwsEcrAuthException(
                        "Wave's service role does not have permission to assume the specified role. " +
                        "Verify the trust policy in your IAM role allows Wave to assume it. " +
                        "Error: ${e.message}", e)

            case 'InvalidParameterValue':
                return new AwsEcrAuthException(
                        "Invalid role ARN or external ID format. " +
                        "Ensure the role ARN follows the pattern 'arn:aws:iam::ACCOUNT_ID:role/ROLE_NAME'. " +
                        "Error: ${e.message}", e)

            case 'RegionDisabledException':
                return new AwsEcrAuthException(
                        "STS is not enabled in the specified region. " +
                        "Enable STS endpoints for this region in AWS. " +
                        "Error: ${e.message}", e)

            case 'ExpiredTokenException':
                return new AwsEcrAuthException(
                        "Temporary credentials have expired. " +
                        "Error: ${e.message}", e)

            default:
                return new AwsEcrAuthException(
                        "STS AssumeRole failed: ${e.message}", e)
        }
    }

    private EcrClient ecrClient(String accessKey, String secretKey, String sessionToken, String region) {
        final credentialsProvider = sessionToken !=null
                ? StaticCredentialsProvider.create(AwsSessionCredentials.create(accessKey, secretKey, sessionToken))
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
        EcrClient.builder()
                .region( Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build()
    }

    private EcrPublicClient ecrPublicClient(String accessKey, String secretKey, String sessionToken, String region) {
        final credentialsProvider = sessionToken !=null
                ? StaticCredentialsProvider.create(AwsSessionCredentials.create(accessKey, secretKey, sessionToken))
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
        EcrPublicClient.builder()
                .region( Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build()
    }

    protected String getLoginToken0(String accessKey, String secretKey, String sessionToken, String region) {
        log.debug "Getting AWS ECR auth token - region=$region; accessKey=$accessKey; sessionToken=${sessionToken ? 'present' : 'none'}"
        final client = ecrClient(accessKey, secretKey, sessionToken, region)
        final resp = client.getAuthorizationToken(GetAuthorizationTokenRequest.builder().build() as GetAuthorizationTokenRequest)
        final encoded = resp.authorizationData().get(0).authorizationToken()
        return new String(encoded.decodeBase64())
    }

    protected String getLoginToken1(String accessKey, String secretKey, String sessionToken, String region) {
        log.debug "Getting AWS ECR public auth token - region=$region; accessKey=$accessKey"
        final client = ecrPublicClient(accessKey, secretKey, sessionToken, region)
        final resp = client.getAuthorizationToken(GetPublicAuthorizationTokenRequest.builder().build() as GetPublicAuthorizationTokenRequest)
        final encoded = resp.authorizationData().authorizationToken()
        return new String(encoded.decodeBase64())
    }

    /**
     * Get AWS ECR login token
     *
     * @param accessKey The AWS access key or IAM role ARN
     * @param secretKey The AWS secret key (or external ID if using role ARN)
     * @param region The AWS region
     * @param isPublic Whether this is for ECR public
     * @return The ECR login token. The token is made up by the aws username and password separated by a `:`
     */
    String getLoginToken(String accessKey, String secretKey, String region, boolean isPublic) {
        assert accessKey, "Missing AWS accessKey argument"
        assert region, "Missing AWS region argument"

        try {
            // Detect if accessKey is actually a role ARN
            if (isRoleArn(accessKey)) {
                return getLoginTokenWithRole(accessKey, secretKey, region, isPublic)
            } else {
                // Static credentials flow (backward compatible)
                assert secretKey, "Missing AWS secretKey argument"
                return getLoginTokenWithStaticCredentials(accessKey, secretKey, region, isPublic)
            }
        }
        catch (Exception e) {
            final type = isPublic ? "ECR public" : "ECR"
            final msg = "Unable to acquire AWS $type authorization token"
            throw new AwsEcrAuthException(msg, e.cause ?: e)
        }
    }

    /**
     * Get login token using static AWS credentials (backward compatible)
     *
     * @param accessKey AWS access key
     * @param secretKey AWS secret key
     * @param region AWS region
     * @param isPublic Whether this is for ECR public
     * @return ECR login token
     */
    protected String getLoginTokenWithStaticCredentials(String accessKey, String secretKey, String region, boolean isPublic) {
        log.trace "Getting ECR login token with static credentials - region=$region"
        final key = new AwsCreds(accessKey, secretKey, null, region, isPublic)
        return cache.getOrCompute(key, (k) -> load(key), cache.duration).value
    }

    /**
     * Get login token by assuming an IAM role
     *
     * @param roleArn The ARN of the role to assume
     * @param externalId External ID for cross-account role assumption (optional)
     * @param region AWS region
     * @param isPublic Whether this is for ECR public
     * @return ECR login token
     */
    protected String getLoginTokenWithRole(String roleArn, String externalId, String region, boolean isPublic) {
        log.debug "Getting ECR login token with role assumption - roleArn=$roleArn; region=$region"

        // Create cache key using roleArn (stable) instead of temporary credentials (which change)
        final key = new AwsCreds(
                roleArn,
                externalId ?: '',
                null,  // no session token for role-based cache key
                region,
                isPublic
        )

        // Use Pair-based getOrCompute to set TTL dynamically from STS credential expiration
        return cache.getOrCompute(key, (String k) -> {
            log.trace "Cache miss for role $roleArn - assuming role to get temporary credentials"

            // Assume the role to get temporary credentials
            final Credentials tempCreds = assumeRole(roleArn, externalId, region)

            // Calculate cache TTL with 5-minute refresh buffer based on STS credential expiration
            final ttl = computeCacheTtl(tempCreds.expiration(), cache.duration)

            // Create temporary credentials for ECR API call
            final tempKey = new AwsCreds(
                    tempCreds.accessKeyId(),
                    tempCreds.secretAccessKey(),
                    tempCreds.sessionToken(),
                    region,
                    isPublic
            )

            // Get ECR auth token using temporary credentials
            final token = load(tempKey)
            return new AbstractTieredCache.Pair<AwsEcrAuthToken, Duration>(token, ttl)
        }).value
    }

    /**
     * Compute cache TTL based on STS credential expiration with 5-minute refresh buffer.
     * Returns the shorter of (time until expiration minus buffer) and maxDuration.
     *
     * @param expiration The STS credential expiration time
     * @param maxDuration The maximum cache duration
     * @return The computed cache TTL
     */
    protected static Duration computeCacheTtl(Instant expiration, Duration maxDuration) {
        if (expiration == null) {
            return maxDuration
        }
        final timeUntilExpiry = Duration.between(Instant.now(), expiration)
        final bufferedTtl = timeUntilExpiry.minus(REFRESH_BUFFER)

        // Ensure TTL is at least MIN_CACHE_TTL
        if (bufferedTtl.compareTo(MIN_CACHE_TTL) < 0) {
            return MIN_CACHE_TTL
        }

        // Use the shorter of buffered TTL and max cache duration
        return bufferedTtl.compareTo(maxDuration) < 0 ? bufferedTtl : maxDuration
    }

    /**
     * Parse AWS ECR host name and return a pair made of account id and region code
     *
     * @param host
     *      The ECR host name e.g. {@code 195996028523.dkr.ecr.eu-west-1.amazonaws.com}
     * @return
     *      A pair holding the AWS account Id as first element and the AWS region as second element.
     *      If the value provided is not a valid ECR host name the {@code null} is returned
     */
    AwsEcrHostInfo getEcrHostInfo(String host) {
        if( !host )
            return null
        final m = AWS_ECR_PRIVATE.matcher(host)
        if( m.find() )
            return new AwsEcrHostInfo(m.group(1), m.group(2))
        final n = AWS_ECR_PUBLIC.matcher(host)
        if( n.find() )
            return new AwsEcrHostInfo(null, 'us-east-1')
        return null
    }

    static boolean isEcrHost(String registry) {
        registry ? AWS_ECR_PRIVATE.matcher(registry).find() || AWS_ECR_PUBLIC.matcher(registry).find() : false
    }
}
