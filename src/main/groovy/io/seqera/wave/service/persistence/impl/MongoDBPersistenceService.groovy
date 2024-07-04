package io.seqera.wave.service.persistence.impl

import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoCollection
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.MongoDBConfig
import io.seqera.wave.core.ContainerDigestPair
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.WaveScanRecord
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements a persistence service based based on SurrealDB
 *
 * @author : Munish Chouhan <munish.chouhan@seqera.io>
 */
@Requires(env='mangodb')
@Primary
@Slf4j
@Singleton
@CompileStatic
class MongoDBPersistenceService implements PersistenceService {

    @Inject
    private MongoClient mongoClient

    @Inject
    private MongoDBConfig mongoDBConfig

    private String WAVE_BUILD_COLLECTION = "wave_build"
    private String WAVE_CONTAINER_REQUEST_COLLECTION = "wave_request"
    private String WAVE_SCAN_COLLECTION = "wave_scan"

    private <T> MongoCollection<T> getCollection(String collectionName, Class<T> type) {
        return mongoClient.getDatabase(mongoDBConfig.databaseName).getCollection(collectionName, type)
    }

    @Override
    void saveBuild(WaveBuildRecord build) {
        try {
            def collection = getCollection(WAVE_BUILD_COLLECTION, WaveBuildRecord.class)
            collection.insertOne(build);
            log.trace("Build request with id '{}' saved record: {}", build.getBuildId(), build);
        } catch (Exception e) {
            log.error("Error saving Build request record {}: {}\n{}", e.getMessage(), build, e);
        }
    }

    @Override
    WaveBuildRecord loadBuild(String buildId) {
        return null
    }

    @Override
    WaveBuildRecord loadBuild(String targetImage, String digest) {
        return null
    }

    @Override
    void saveContainerRequest(String token, WaveContainerRecord data) {

    }

    @Override
    void updateContainerRequest(String token, ContainerDigestPair digest) {

    }

    @Override
    WaveContainerRecord loadContainerRequest(String token) {
        return null
    }

    @Override
    void createScanRecord(WaveScanRecord scanRecord) {

    }

    @Override
    void updateScanRecord(WaveScanRecord scanRecord) {

    }

    @Override
    WaveScanRecord loadScanRecord(String scanId) {
        return null
    }
}
