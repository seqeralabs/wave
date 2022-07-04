package io.seqera.wave.auth.aws

import java.util.concurrent.TimeUnit

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.ecr.AmazonECR
import com.amazonaws.services.ecr.AmazonECRClientBuilder
import com.amazonaws.services.ecr.model.GetAuthorizationTokenRequest
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import io.seqera.wave.auth.RegistryCredentials
import io.seqera.wave.auth.SimpleRegistryCredentials
import jakarta.inject.Singleton

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
class AwsRegistryCredentialsProvider {

    private CacheLoader<String, AmazonECR> loader = new CacheLoader<String, AmazonECR>() {
        @Override
        AmazonECR load(String keys) throws Exception {
            final tokens = keys.tokenize(';')
            client(tokens[0],tokens[1],tokens[2])
        }
    }

    private LoadingCache<String, AmazonECR> cache = CacheBuilder<URI, AmazonECR>
            .newBuilder()
            .maximumSize(1_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build(loader)


    private AmazonECR client(String accessKey, String secretKey, String region) {
        AmazonECRClientBuilder
                .standard()
                .withRegion(region)
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
                .build()
    }


    protected String getLoginToken(String accessKey, String secretKey, String region) {
        final resp = client(accessKey,secretKey,region)
                .getAuthorizationToken(new GetAuthorizationTokenRequest())
        final encoded = resp.getAuthorizationData().get(0).getAuthorizationToken()
        return new String(encoded.decodeBase64())
    }

    RegistryCredentials getAwsCredentials(String accessKey, String secretKey, String region) {
        final username = 'AWS'
        final token = getLoginToken(accessKey, secretKey, region)
        new SimpleRegistryCredentials(username, token)
    }

}
