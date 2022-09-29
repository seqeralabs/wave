package io.seqera.wave.stats.surrealdb

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.stats.BuildBean
import io.seqera.wave.stats.Storage
import jakarta.inject.Inject
import jakarta.inject.Singleton


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Requires(env='surreal')
@Slf4j
@Singleton
@CompileStatic
class SurrealStorage implements Storage{

    SurrealClient surrealClient

    @Value('${stats.surrealdb.user}')
    String user

    @Value('${stats.surrealdb.password}')
    String password

    SurrealStorage(SurrealClient surrealClient) {
        this.surrealClient = surrealClient
    }

    private String getAuthorization(){
        "Basic "+"$user:$password".bytes.encodeBase64()
    }

    @Override
    void addBuild(BuildBean build) {
        surrealClient.insertBuildAsync(authorization, build).subscribe({result->
            log.debug "BuildBean saved, {}", result
        }, {error->
            log.error "Error saving build bean {}", build
        })
    }
}
