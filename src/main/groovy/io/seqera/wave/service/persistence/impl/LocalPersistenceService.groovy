package io.seqera.wave.service.persistence.impl


import io.seqera.wave.service.persistence.BuildRecord
import io.seqera.wave.service.persistence.CondaRecord
import io.seqera.wave.service.persistence.PersistenceService
import jakarta.inject.Singleton
/**
 * Basic persistence for dev purpose
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
class LocalPersistenceService implements PersistenceService {

    private Map<String,BuildRecord> buildStore = new HashMap<>()

    private Map<String, CondaRecord> condaStore = new HashMap<>()

    @Override
    void saveBuild(BuildRecord record) {
        buildStore[record.buildId] = record
    }

    @Override
    BuildRecord loadBuild(String buildId) {
        return buildStore.get(buildId)
    }

    CondaRecord loadConda(String condaId) {
        return condaStore.get(condaId)
    }

    void saveConda(CondaRecord record) {
        condaStore[record.id] = record
    }
}
