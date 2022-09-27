package io.seqera.wave.tower.client

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.annotation.Client

/**
 * Declarative Tower API client
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(env = 'tower')
@Client('${tower.api.endpoint}')
interface TowerClient {

    @Get('/user-info')
    UserInfoResponse userInfo(@Header("Authorization") String authorization)
    
}
