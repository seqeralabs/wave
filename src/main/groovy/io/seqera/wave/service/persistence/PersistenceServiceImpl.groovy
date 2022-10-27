package io.seqera.wave.service.persistence

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.event.ApplicationStartupEvent
import io.micronaut.runtime.event.annotation.EventListener
import io.seqera.wave.service.builder.BuildEvent
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Implements a persistince service based based on SurrealDB
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Requires(env='surrealdb')
@Primary
@Slf4j
@Singleton
@CompileStatic
class PersistenceServiceImpl implements PersistenceService {

    @Inject
    private SurrealDbClient surrealDb

    @Value('${surrealdb.user}')
    private String user

    @Value('${surrealdb.password}')
    private String password

    @Nullable
    @Value('${surrealdb.init-db}')
    private Boolean initDb

    @EventListener
    void onApplicationStartup(ApplicationStartupEvent event) {
        if (initDb)
            initializeDb()
    }

    @EventListener
    void onBuildEvent(BuildEvent event) {
        saveBuild(BuildRecord.fromEvent(event))
    }

    void initializeDb(){
        final result = surrealDb.sql(authorization, "define table wave_build SCHEMALESS")
        if( result.status != "OK")
            throw new IllegalStateException("Unable to initiliase SurrealDB - cause: $result")
    }

    private String getAuthorization() {
        "Basic "+"$user:$password".bytes.encodeBase64()
    }

    @Override
    void saveBuild(BuildRecord build) {
        surrealDb.insertBuildAsync(authorization, build).subscribe({ result->
            log.info "Build record saved ${result}"
        }, {error->
            def msg = error.message
            if( error instanceof HttpClientResponseException ){
                msg += ":\n $error.response.body"
            }
            log.error "Error saving build record ${msg}\n${build}", error
        })
    }
}
