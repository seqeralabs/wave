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

import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.util.StringUtils
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ecr.EcrClient
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenRequest
import software.amazon.awssdk.services.ecrpublic.EcrPublicClient
import software.amazon.awssdk.services.ecrpublic.model.GetAuthorizationTokenRequest as GetPublicAuthorizationTokenRequest
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

    @Canonical
    private static class AwsCreds {
        String accessKey
        String secretKey
        String region
        boolean ecrPublic
    }

    @Canonical
    static class AwsEcrHostInfo {
        String account
        String region
    }

    private CacheLoader<AwsCreds, String> loader = new CacheLoader<AwsCreds, String>() {
        @Override
        String load(AwsCreds creds) throws Exception {
            return creds.ecrPublic
                    ? getLoginToken1(creds.accessKey, creds.secretKey, creds.region)
                    : getLoginToken0(creds.accessKey, creds.secretKey, creds.region)
        }
    }

    @Inject
    @Named(TaskExecutors.BLOCKING)
    private ExecutorService ioExecutor

    // FIXME https://github.com/seqeralabs/wave/issues/747
    private AsyncLoadingCache<AwsCreds, String> cache

    @PostConstruct
    private void init() {
        cache = Caffeine
                .newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(3, TimeUnit.HOURS)
                .executor(ioExecutor)
                .buildAsync(loader)
    }

    private EcrClient ecrClient(String accessKey, String secretKey, String region) {
        EcrClient.builder()
                .region( Region.of(region))
                .credentialsProvider( StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .build()
    }

    private EcrPublicClient ecrPublicClient(String accessKey, String secretKey, String region) {
        EcrPublicClient.builder()
                .region( Region.of(region))
                .credentialsProvider( StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .build()
    }

    protected String getLoginToken0(String accessKey, String secretKey, String region) {
        log.debug "Getting AWS ECR auth token - region=$region; accessKey=$accessKey; secretKey=${StringUtils.redact(secretKey)}"
        final client = ecrClient(accessKey,secretKey,region)
        final resp = client.getAuthorizationToken(GetAuthorizationTokenRequest.builder().build() as GetAuthorizationTokenRequest)
        final encoded = resp.authorizationData().get(0).authorizationToken()
        return new String(encoded.decodeBase64())
    }

    protected String getLoginToken1(String accessKey, String secretKey, String region) {
        log.debug "Getting AWS ECR public auth token - region=$region; accessKey=$accessKey; secretKey=${StringUtils.redact(secretKey)}"
        final client = ecrPublicClient(accessKey,secretKey,region)
        final resp = client.getAuthorizationToken(GetPublicAuthorizationTokenRequest.builder().build() as GetPublicAuthorizationTokenRequest)
        final encoded = resp.authorizationData().authorizationToken()
        return new String(encoded.decodeBase64())
    }

    /**
     * Get AWS ECR login token
     *
     * @param accessKey The AWS access key
     * @param secretKey The AWS secret key
     * @param region The AWS region
     * @return The ECR login token. The token is made up by the aws username and password separated by a `:`
     */
    String getLoginToken(String accessKey, String secretKey, String region, boolean isPublic) {
        assert accessKey, "Missing AWS accessKey argument"
        assert secretKey, "Missing AWS secretKey argument"
        assert region, "Missing AWS region argument"

        try {
            // get the token from the cache, if missing the it's automatically
            // fetch using the AWS ECR client
            // FIXME https://github.com/seqeralabs/wave/issues/747
            return cache.synchronous().get(new AwsCreds(accessKey,secretKey,region,isPublic))
        }
        catch (Exception e) {
            final type = isPublic ? "ECR public" : "ECR"
            final msg = "Unable to acquire AWS $type authorization token"
            throw new AwsEcrAuthException(msg, e.cause ?: e)
        }
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
