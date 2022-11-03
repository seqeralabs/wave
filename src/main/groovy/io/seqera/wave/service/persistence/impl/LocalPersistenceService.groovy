package io.seqera.wave.service.persistence.impl


import io.seqera.wave.service.persistence.BuildRecord
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.PullRecord
import jakarta.inject.Singleton
/**
 * Basic persistence for dev purpose
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
class LocalPersistenceService implements PersistenceService {

    private Map<String,BuildRecord> store = new HashMap<>()


    @Override
    void saveBuild(BuildRecord record) {
        store[record.buildId] = record
    }

    @Override
    BuildRecord loadBuild(String buildId) {
        return store.get(buildId)
    }

    @Override
    void savePull(PullRecord pull) {
        // do nothing
    }
}
