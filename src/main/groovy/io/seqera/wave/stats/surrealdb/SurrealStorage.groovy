package io.seqera.wave.stats.surrealdb

import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.runtime.event.ApplicationStartupEvent
import io.seqera.wave.stats.BuildBean
import io.seqera.wave.stats.Storage
import jakarta.inject.Inject
import jakarta.inject.Singleton


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Requires(env='surreal')
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
                   @Value('${stats.surrealdb.user}')String user,
                   @Value('${stats.surrealdb.password}')String password,
                   @Value('${stats.surrealdb.init-db}')boolean initDb) {
        this.surrealClient = surrealClient
        this.user = user
        this.password = password
        this.initDb = initDb
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
    void addBuild(BuildBean build) {
        surrealClient.insertBuildAsync(authorization, build).subscribe({result->
            log.debug "BuildBean saved, {}", result
        }, {error->
            log.error "Error saving build bean {}", build, error
        })
    }
}
