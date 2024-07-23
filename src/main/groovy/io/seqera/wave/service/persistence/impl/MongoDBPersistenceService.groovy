/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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

package io.seqera.wave.service.persistence.impl

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
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
import org.bson.Document
/**
 * Implements a persistence service based based on SurrealDB
 *
 * @author : Munish Chouhan <munish.chouhan@seqera.io>
 */
@Requires(env='mongodb')
@Primary
@Slf4j
@Singleton
@CompileStatic
class MongoDBPersistenceService implements PersistenceService {

    @Inject
    private MongoClient mongoClient

    @Inject
    private MongoDBConfig mongoDBConfig

    private final String WAVE_BUILD_COLLECTION = "wave_build"
    private String WAVE_CONTAINER_COLLECTION = "wave_request"
    private String WAVE_SCAN_COLLECTION = "wave_scan"

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
        try {
            def collection = getCollection(WAVE_BUILD_COLLECTION, WaveBuildRecord.class)
            return collection.find(Filters.eq("_id", buildId)).first()
        } catch (Exception e) {
            log.error("Error fetching Build request record {}: {}", e.getMessage(), e);
        }
        return null
    }

    @Override
    WaveBuildRecord loadBuild(String targetImage, String digest) {
        return null
    }

    @Override
    void saveContainerRequest(String token, WaveContainerRecord data) {
        try {
            def collection = getCollection(WAVE_CONTAINER_COLLECTION, WaveContainerRecord.class)
            collection.insertOne(data);
            log.trace("Container request with id '{}' saved record: {}", data.token, data);
        } catch (Exception e) {
            log.error("Error saving container request record {}: {}\n{}", e.getMessage(), data, e);
        }
    }

    @Override
    void updateContainerRequest(String token, ContainerDigestPair digest) {
        try {
            def collection = getCollection(WAVE_CONTAINER_COLLECTION, WaveContainerRecord.class)
            collection.updateOne(Filters.eq("token", token), new Document("\$set", new Document("sourceDigest", digest.source).append("waveDigest", digest.target)))
            log.trace("Container request with id '{}' updated digest: {}", token, digest);
        } catch (Exception e) {
            log.error("Error updating Container request record {}: {}\n{}", e.getMessage(), token, e);
        }
    }

    @Override
    WaveContainerRecord loadContainerRequest(String token) {
        try {
            def collection = getCollection(WAVE_CONTAINER_COLLECTION, WaveContainerRecord.class)
            return collection.find(Filters.eq("_id", token)).first()
        } catch (Exception e) {
            log.error("Error fetching container request record {}: {}", e.getMessage(), e);
        }
        return null
    }

    @Override
    void createScanRecord(WaveScanRecord scanRecord) {
        try {
            def collection = getCollection(WAVE_SCAN_COLLECTION, WaveScanRecord.class)
            collection.insertOne(scanRecord);
            log.trace("Container scan with id '{}' saved record: {}", scanRecord.id, scanRecord);
        } catch (Exception e) {
            log.error("Error saving container scan record {}: {}\n{}", e.getMessage(), scanRecord, e);
        }
    }

    @Override
    void updateScanRecord(WaveScanRecord scanRecord) {

    }

    @Override
    WaveScanRecord loadScanRecord(String scanId) {
        try {
            def collection = getCollection(WAVE_SCAN_COLLECTION, WaveScanRecord.class)
            return collection.find(Filters.eq("_id", scanId)).first()
        } catch (Exception e) {
            log.error("Error fetching container scan record {}: {}", e.getMessage(), e);
        }
        return null
    }

    private <T> MongoCollection<T> getCollection(String collectionName, Class<T> type) {
        return mongoClient.getDatabase(mongoDBConfig.databaseName).getCollection(collectionName, type)
    }
}
