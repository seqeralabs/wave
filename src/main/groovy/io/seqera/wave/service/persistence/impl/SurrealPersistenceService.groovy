package io.seqera.wave.service.persistence.impl

import com.fasterxml.jackson.core.type.TypeReference
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.event.ApplicationStartupEvent
import io.micronaut.runtime.event.annotation.EventListener
import io.seqera.wave.service.persistence.BuildRecord
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.util.JacksonHelper
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
class SurrealPersistenceService implements PersistenceService {

    @Inject
    private SurrealClient surrealDb

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

    void initializeDb(){
        final result = surrealDb.sqlAsMap(authorization, "define table wave_build SCHEMALESS")
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

    void saveBuildBlocking(BuildRecord record) {
        surrealDb.insertBuild(getAuthorization(), record)
    }

    BuildRecord loadBuild(String buildId) {
        if( !buildId )
            throw new IllegalArgumentException('Missing build id argument')
        final query = "select * from wave_build where buildId = '$buildId'"
        final json = surrealDb.sqlAsString(getAuthorization(), query)
        final type = new TypeReference<ArrayList<SurrealResult<BuildRecord>>>() {}
        final data= json ? JacksonHelper.fromJson(patchDuration(json), type) : null
        final result = data && data[0].result ? data[0].result[0] : null
        return result
    }

    static protected String patchDuration(String value) {
        if( !value )
            return value
        // Yet another SurrealDB bug: it wraps number values with double quotes as a string
        value.replaceAll(/"duration":"(\d+\.\d+)"/,'"duration":$1')
    }
}
