package io.seqera.wave.stats.surrealdb


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.exceptions.HttpException
import io.micronaut.runtime.event.ApplicationStartupEvent
import io.seqera.wave.stats.BuildRecord
import io.seqera.wave.stats.Storage
import jakarta.inject.Singleton


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Requires(env='surrealdb')
@Primary
@Slf4j
@Singleton
@CompileStatic
class SurrealStorage implements Storage, ApplicationEventListener<ApplicationStartupEvent>{

    SurrealClient surrealClient

    String user

    String password

    boolean initDb
    SurrealStorage(SurrealClient surrealClient,
                   @Value('${surrealdb.user}')String user,
                   @Value('${surrealdb.password}')String password,
                   @Nullable @Value('${surrealdb.init-db}')java.util.Optional<Boolean> initDb) {
        this.surrealClient = surrealClient
        this.user = user
        this.password = password
        this.initDb = initDb.present ? initDb.get() : false
    }

    @Override
    void onApplicationEvent(ApplicationStartupEvent event) {
        if (initDb)
            initializeDb()
    }

    void initializeDb(){
        try {
            def result = surrealClient.sql(authorization, "define table $SurrealConstants.BUILD_WAVE_TABLENAME SCHEMALESS")
            if( result.status != "OK")
                throw new RuntimeException("$result")
        }catch(Exception e){
            log.error "Unable to initialize stats database ",e
        }
    }

    private String getAuthorization(){
        "Basic "+"$user:$password".bytes.encodeBase64()
    }

    @Override
    void addBuild(BuildRecord build) {
        surrealClient.insertBuildAsync(authorization, build).subscribe({result->
            log.info "BuildBean saved, {}", result
        }, {error->
            def msg = error.message
            if( error instanceof HttpClientResponseException){
                msg += ":\n $error.response.body"
            }
            log.error "Error saving build bean {}\n{}", msg, build, error
        })
    }
}
