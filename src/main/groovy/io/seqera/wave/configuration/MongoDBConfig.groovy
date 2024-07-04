package io.seqera.wave.configuration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
/**
 * Container Scan service settings
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
@Singleton
@Slf4j
class MongoDBConfig {

    /**
     * MongoDB database name
     */
    @Value('${mongodb.database.name}')
    String databaseName

    /**
     * MongoDB uri
     */
    @Value('${mongodb.uri}')
    String uri
}
