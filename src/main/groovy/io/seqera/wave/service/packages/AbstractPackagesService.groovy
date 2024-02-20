/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

package io.seqera.wave.service.packages

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.util.logging.Slf4j
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.configuration.PackagesConfig
import io.seqera.wave.service.persistence.CondaPackageRecord
import io.seqera.wave.service.persistence.PersistenceService
import jakarta.inject.Inject
import jakarta.inject.Named

/**
 * Implements base logic for fetching and storing
 * Package dependencies metadata used to build containers
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
abstract class AbstractPackagesService implements PackagesService{

    @Inject
    private BuildConfig buildConfig

    @Inject
    private PackagesConfig packagesConfig

    @Inject
    private PersistenceService persistenceService

    @Inject
    @Named(TaskExecutors.IO)
    private ExecutorService executor

    protected List<String> fetchCommand(String channel, Path target) {
        List.of('bash','-c', "conda search -q -c $channel > $target".toString())
    }

    @Override
    void fetchCondaPackages() {
        final workDir = Path.of(buildConfig.buildWorkspace).resolve('conda-' + System.currentTimeMillis()).toAbsolutePath()
        // create the work dir
        Files.createDirectory(workDir)

        for( String it : packagesConfig.channels ) {
            processChannel(it, workDir)
        }

    }

    protected void processChannel(String channel, Path workDir) {
        final target = "ch-${channel}.txt"
        def condaFile = workDir.resolve(target)
        run(fetchCommand(channel, condaFile), workDir)
        // read the result file and parse it
        CompletableFuture
                .supplyAsync( ()->processResult(condaFile), executor)
    }

    protected void processResult(Path file) {
        try(final reader = Files.newBufferedReader(file)) {
            String line
            boolean begin=false
            //Map to keep track of packages
            Map<String, CondaPackageRecord> packages = new HashMap<>()
            while( (line=reader.readLine()) ) {
                if( !begin ) {
                    begin = line.startsWith("# Name")
                    continue
                }
                final items = line.tokenize(' ')
                def entry = new CondaPackageRecord(items[3], items[0], items[1], [items[2]].toList())
                if( packages.containsKey(entry.id) ) {
                    packages.get(entry.id).builds.add(items[2])
                }else{
                    packages.put(entry.id, entry)
                }
            }
            if ( !packages.isEmpty() ) {
                persistenceService.saveCondaPackagesChunks(packages.values().collect(), 5000)
                log.debug("Conda packages saved")
            }
        }
    }

    @Override
    List<CondaPackageRecord> findCondaPackage(String search) {
        persistenceService.findCondaPackage(search)
    }
    abstract protected void run(List<String> command, Path workDir)

}
