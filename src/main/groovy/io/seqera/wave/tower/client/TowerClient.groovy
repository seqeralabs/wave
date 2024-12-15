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

package io.seqera.wave.tower.client

import java.util.concurrent.CompletableFuture

import com.google.common.hash.Hashing
import groovy.transform.CompileStatic
import io.micronaut.cache.annotation.Cacheable
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.client.connector.TowerConnector
import io.seqera.wave.tower.compute.DescribeWorkflowLaunchResponse
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.apache.commons.lang3.StringUtils
/**
 * Implement a client to interact with Tower services
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Singleton
@CompileStatic
class TowerClient {

    @Inject
    private TowerConnector connector

    protected <T> CompletableFuture<T> getAsync(URI uri, String endpoint, @Nullable JwtAuth authorization, Class<T> type) {
        assert uri, "Missing uri argument"
        assert endpoint, "Missing endpoint argument"
        return connector.sendAsync(endpoint, uri, authorization, type)
    }

    @Cacheable(value = 'cache-tower-client-short', atomic = true, parameters = ['cacheKey'])
    protected <T> CompletableFuture<T> getCacheShort(URI uri, String endpoint, @Nullable JwtAuth authorization, Class<T> type, String cacheKey) {
        return getAsync(uri, endpoint, authorization, type)
    }

    @Cacheable(value = 'cache-tower-client-long', atomic = true, parameters = ['cacheKey'])
    protected <T> CompletableFuture<T> getCacheLong(URI uri, String endpoint, @Nullable JwtAuth authorization, Class<T> type, String cacheKey) {
        return getAsync(uri, endpoint, authorization, type)
    }

    CompletableFuture<UserInfoResponse> userInfo(String towerEndpoint, JwtAuth authorization) {
        final uri = userInfoEndpoint(towerEndpoint)
        final k = makeKey(uri, authorization.key, null, null)
        return getCacheLong(uri, towerEndpoint, authorization, UserInfoResponse, k)
    }

    CompletableFuture<ListCredentialsResponse> listCredentials(String towerEndpoint, JwtAuth authorization, Long workspaceId, String workflowId) {
        final uri = listCredentialsEndpoint(towerEndpoint, workspaceId)
        final k = makeKey(uri, authorization.key, workspaceId, workflowId)
        return workflowId
                ? getCacheLong(uri, towerEndpoint, authorization, ListCredentialsResponse, k)
                : getCacheShort(uri, towerEndpoint, authorization, ListCredentialsResponse, k)
    }

    CompletableFuture<GetCredentialsKeysResponse> fetchEncryptedCredentials(String towerEndpoint, JwtAuth authorization, String credentialsId, String pairingId, Long workspaceId, String workflowId) {
        final uri = fetchCredentialsEndpoint(towerEndpoint, credentialsId, pairingId, workspaceId)
        final k = makeKey(uri, authorization.key, workspaceId, workflowId)
        return workflowId
                ? getCacheLong(uri, towerEndpoint, authorization, GetCredentialsKeysResponse, k)
                : getCacheShort(uri, towerEndpoint, authorization, GetCredentialsKeysResponse, k)
    }

    protected static URI fetchCredentialsEndpoint(String towerEndpoint, String credentialsId, String pairingId, Long workspaceId) {
        if( !towerEndpoint )
            throw new IllegalArgumentException("Missing towerEndpoint argument")
        if (!credentialsId)
            throw new IllegalArgumentException("Missing credentialsId argument")
        if (!pairingId)
            throw new IllegalArgumentException("Missing encryptionKey argument")

        def uri = "${checkEndpoint(towerEndpoint)}/credentials/$credentialsId/keys?pairingId=$pairingId"
        if( workspaceId!=null )
            uri += "&workspaceId=$workspaceId"

        return URI.create(uri)
    }

    protected static URI listCredentialsEndpoint(String towerEndpoint, Long workspaceId) {
        def uri = "${checkEndpoint(towerEndpoint)}/credentials"
        if( workspaceId!=null )
            uri += "?workspaceId=$workspaceId"
        return URI.create(uri)
    }

    protected static URI userInfoEndpoint(String towerEndpoint) {
        return URI.create("${checkEndpoint(towerEndpoint)}/user-info")
    }

    static String checkEndpoint(String endpoint) {
        if( !endpoint )
            throw new IllegalArgumentException("Missing endpoint argument")
        if( !endpoint.startsWith('http://') && !endpoint.startsWith('https://') )
            throw new IllegalArgumentException("Endpoint should start with HTTP or HTTPS protocol — offending value: '$endpoint'")

        StringUtils.removeEnd(endpoint, "/")
    }

    CompletableFuture<DescribeWorkflowLaunchResponse> describeWorkflowLaunch(String towerEndpoint, JwtAuth authorization, String workflowId) {
        final uri = workflowLaunchEndpoint(towerEndpoint,workflowId)
        final k = makeKey(uri, authorization.key, null, workflowId)
        return getCacheShort(uri, towerEndpoint, authorization, DescribeWorkflowLaunchResponse.class, k)
    }

    protected static URI workflowLaunchEndpoint(String towerEndpoint, String workflowId) {
        return URI.create("${checkEndpoint(towerEndpoint)}/workflow/${workflowId}/launch")
    }

    protected String makeKey(Object... keys) {
        final h = Hashing .sipHash24() .newHasher()
        for( Object it :  keys) {
            if( it!=null )
                h.putUnencodedChars(it.toString())
            h.putUnencodedChars('/')
        }
        return h.hash()
    }
}
