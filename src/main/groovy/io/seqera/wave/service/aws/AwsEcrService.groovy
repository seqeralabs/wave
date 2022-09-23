package io.seqera.wave.service.aws

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.util.concurrent.UncheckedExecutionException
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.util.StringUtils
import jakarta.inject.Singleton
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ecr.EcrClient
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenRequest
/**
 * Implement AWS ECR login service
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class AwsEcrService {

    static final private Pattern AWS_ECR = ~/^(\d+)\.dkr\.ecr\.([a-z\-\d]+)\.amazonaws\.com/

    @Canonical
    private static class AwsCreds {
        String accessKey
        String secretKey
        String region
    }

    @Canonical
    static class AwsEcrHostInfo {
        String account
        String region
    }

    private CacheLoader<AwsCreds, String> loader = new CacheLoader<AwsCreds, String>() {
        @Override
        String load(AwsCreds creds) throws Exception {
            getLoginToken0(creds.accessKey, creds.secretKey, creds.region)
        }
    }

    private LoadingCache<AwsCreds, String> cache = CacheBuilder<AwsCreds, String>
            .newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(3, TimeUnit.HOURS)
            .build(loader)


    private EcrClient ecrClient(String accessKey, String secretKey, String region) {
        AwsCredentialsProvider credentialsProvider = accessKey && secretKey ?
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)) :
                DefaultCredentialsProvider.create()

        EcrClient.builder()
                .region( Region.of(region))
                .credentialsProvider( credentialsProvider )
                .build()
    }


    protected String getLoginToken0(String accessKey, String secretKey, String region) {
        log.debug "Getting AWS ECR auth token - region=$region; accessKey=$accessKey; secretKey=${StringUtils.redact(secretKey)}"
        final client = ecrClient(accessKey,secretKey,region)
        final resp = client.getAuthorizationToken(GetAuthorizationTokenRequest.builder().build() as GetAuthorizationTokenRequest)
        final encoded = resp.authorizationData().get(0).authorizationToken()
        return new String(encoded.decodeBase64())
    }

    /**
     * Get AWS ECR login token
     *
     * @param accessKey The AWS access key, if null StandardCred will be used
     * @param secretKey The AWS secret key, if null StandardCred will be used
     * @param region The AWS region
     * @return The ECR login token. The token is made up by the aws username and password separated by a `:`
     */
    String getLoginToken(String accessKey, String secretKey, String region) {
        //assert accessKey, "Missing AWS accessKey argument"
        //assert secretKey, "Missing AWS secretKet argument"
        assert region, "Missing AWS region argument"

        try {
            // get the token from the cache, if missing the it's automatically
            // fetch using the AWS ECR client
            return cache.get(new AwsCreds(accessKey,secretKey,region))
        }
        catch (UncheckedExecutionException | ExecutionException e) {
            throw e.cause
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
        final m = AWS_ECR.matcher(host)
        if( !m.find() )
            return null
        return new AwsEcrHostInfo(m.group(1), m.group(2))
    }

    static boolean isEcrHost(String registry) {
        registry ? AWS_ECR.matcher(registry).find() : false
    }
}
